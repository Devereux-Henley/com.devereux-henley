(ns com.devereux-henley.rts-domain.handlers.replay
  "Parses uploaded `.replay` files via the Rust `tw-replay-parser` binary and
  binds the parsed result to a tournament match via per-game match_game rows."
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as string]
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.handlers.draft :as handlers.draft]
   [com.devereux-henley.rts-domain.handlers.tournament :as handlers.tournament]
   [com.devereux-henley.rts-domain.rules.tournament :as rules.tournament]
   [com.devereux-henley.rts-domain.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract]
   [jsonista.core :as jsonista]
   [malli.core :as m])
  (:import
   [java.time Instant]))

(def ^:private json-mapper
  ;; The Rust binary emits snake_case JSON keys; we want Clojure-idiomatic
  ;; kebab-case keywords on the domain side.
  (jsonista/object-mapper
   {:decode-key-fn (fn [k] (keyword (string/replace k "_" "-")))}))

(defn- iso-played-at
  "Renders the CLI's {year month day hour minute second} map as an ISO-8601
  date-time string. Replay timestamps are local to the player's machine; we
  store them verbatim without timezone conversion (the game doesn't emit one).
  Returns nil if any component is missing."
  [{:keys [year month day hour minute second]}]
  (when (every? some? [year month day hour minute second])
    (format "%04d-%02d-%02dT%02d:%02d:%02d"
            year month day hour minute second)))

(defn parse-replay-file
  "Invokes the tw-replay-parser binary on the given file path and returns the
  decoded CLJ map. Throws ex-info on non-zero exit.

  The binary path is read from `:replay-parser-bin` in `dependencies`, falling
  back to the REPLAY_PARSER_BIN env var or \"tw-replay-parser\" on PATH."
  [dependencies file-path]
  (let [bin    (or (:replay-parser-bin dependencies)
                   (System/getenv "REPLAY_PARSER_BIN")
                   "tw-replay-parser")
        result (shell/sh bin file-path)]
    (if (zero? (:exit result))
      (jsonista/read-value (:out result) json-mapper)
      (throw (ex-info "replay parse failed"
                      {:error/kind :error/invalid
                       :exit       (:exit result)
                       :stderr     (:err result)})))))

(defn- persist-replay
  "Transacts a `:replay/*` entity from a parsed map. Returns the
  inserted replay's flat-key shape."
  [dependencies {:keys [parsed source-name uploaded-by-sub]}]
  (let [now (Instant/now)]
    (db/create-replay!
     (:datalog-connection dependencies)
     {:eid                           (random-uuid)
      :match-id-external             (or (:match-id parsed)
                                         source-name
                                         "unknown")
      :played-at                     (or (iso-played-at (:played-at parsed))
                                         (str now))
      :victory-condition             (:victory-condition parsed)
      :parser-format                 (:format parsed)
      :parsed-data                   parsed
      :uploader-local-alliance-index (:uploader-local-alliance-index parsed)
      :uploaded-by-sub               uploaded-by-sub})))

(defn- valid-winner?
  "A declared winner-sub must equal one of the match's player subs."
  [{:keys [player-one-sub player-two-sub]} winner-sub]
  (or (= winner-sub player-one-sub)
      (= winner-sub player-two-sub)))

(defn- short-circuit-error
  [message]
  {:type :match-record/error :message message})

;; ─── Auto-create drafts on submit ───────────────────────────────────────────
;;
;; Each game in the submission yields a pair of drafts (one per alliance →
;; one per match-side player). The drafts capture the units that were
;; actually played plus the parser-emitted spell list and the suffix-derived
;; mount. Drafts go read-only the moment they're attached to a `match_game`
;; row (see `get-draft-lock-info`).

(def ^:private domination-victory-conditions
  "Engine `victory_condition` values that map to the seeded Domination
  game mode. Anything outside the set defaults to `Land Battle` — the
  draft is a post-facto record of a played game, so the mode label is
  cosmetic when the mapping is ambiguous."
  #{"BATTLE_SETUP_VICTORY_CONDITION_CAPTURE_LOCATION_SCORE"})

(defn pick-game-mode
  "Picks a game-mode entity from `game-modes` keyed on the parsed
  `victory_condition`. Falls back to the first game-mode (typically
  `Land Battle`) when no mapping matches."
  [game-modes victory-condition]
  (let [target (if (contains? domination-victory-conditions victory-condition)
                 "Domination"
                 "Land Battle")]
    (or (first (filter #(= target (:name %)) game-modes))
        (first game-modes))))

(defn- alliance-sides
  "Builds a 2-vector of {:player-sub :alliance} pairs aligned with
  match.player_one_sub / player_two_sub.  Mirrors `build-game-context`
  in the post-match modal view: the parser's
  `uploader-local-alliance-index` plus the submission's `uploaded-by-sub`
  tells us which alliance is the uploader's, and the match's
  `player_one_sub` / `player_two_sub` finishes the mapping."
  [parsed match uploaded-by-sub]
  (let [a0           (get-in parsed [:alliances 0] {})
        a1           (get-in parsed [:alliances 1] {})
        local-idx    (or (:uploader-local-alliance-index parsed) 0)
        uploader-a0? (zero? local-idx)
        uploader-p1? (= uploaded-by-sub (:player-one-sub match))
        p1-alliance  (if (= uploader-p1? uploader-a0?) a0 a1)
        p2-alliance  (if (identical? p1-alliance a0) a1 a0)]
    [{:player-sub (:player-one-sub match) :alliance p1-alliance}
     {:player-sub (:player-two-sub match) :alliance p2-alliance}]))

(defn- parsed-key-prefixes
  "Successively shorter `_`-delimited prefixes of `k` so a mount-suffixed
  parser key (e.g. `..._sorcerer_prophet_fire_great_taurus`) can
  resolve against the un-mounted base unit row."
  [k]
  (let [parts (string/split k #"_")]
    (when (> (count parts) 1)
      (mapv (fn [n] (string/join "_" (take n parts)))
            (range (dec (count parts)) 0 -1)))))

(defn- resolve-unit-row
  "Exact lookup, then longest-prefix fallback against `key->row`."
  [key->row k]
  (or (get key->row k)
      (some #(get key->row %) (parsed-key-prefixes k))))

(defn- mount-suffix
  "Trailing portion of `parsed-key` after `base-key_` (i.e. the mount
  tag the engine appended).  Returns nil when `parsed-key` doesn't
  extend `base-key`."
  [base-key parsed-key]
  (when (and (seq base-key) (seq parsed-key))
    (let [prefix (str base-key "_")]
      (when (and (string/starts-with? parsed-key prefix)
                 (> (count parsed-key) (count prefix)))
        (subs parsed-key (count prefix))))))

(defn- match-mount-key
  "Finds the mount row in `mount-rows` whose key tail (after stripping
  the canonical `mount_` prefix) equals `suffix`. Returns the row's
  `:key` (the persisted ancillary id) or nil so callers can omit the
  field when no mount applies."
  [mount-rows suffix]
  (when suffix
    (some (fn [m]
            (let [k (:key m)]
              (when (and k (= (string/replace-first k #"^mount_" "") suffix))
                k)))
          mount-rows)))

(defn- parsed-unit->entry
  "Shapes a single parsed unit into a draft_state entry. Returns nil for
  units the seed table can't resolve so the draft only captures
  recognised rows (a missing seed key is operator noise, not a draft
  failure). The parser's `:level`/`:spells` carry through; the mount is
  derived from the parsed key's suffix vs the resolved unit row, and
  items wait on issue #81.

  `:total-cost` is computed against the same domain function the unit
  panel uses, so the slot card and the panel agree by construction.
  The parser-emitted true engine cost (whichever of `:adjusted-cost` /
  `:cost` is available) is preserved on `:engine-cost` as an audit
  signal — a divergence between `:engine-cost` and the recomputed
  `:total-cost` flags either a seed-data gap or an app computation
  bug."
  [parsed-unit key->row eid->mount-rows conn]
  (let [parsed-key (:key parsed-unit)]
    (when-let [row (resolve-unit-row key->row parsed-key)]
      (let [mount-key  (match-mount-key (get eid->mount-rows (:eid row))
                                        (mount-suffix (:key row) parsed-key))
            level      (or (:level parsed-unit) 0)
            spells     (vec (:spells parsed-unit))
            selections {:mount     mount-key
                        :level     level
                        :abilities []
                        :spells    spells
                        :items     []}
            total      (handlers.draft/compute-unit-total-cost row selections conn)]
        {:entry-eid   (random-uuid)
         :unit-eid    (:eid row)
         :mount       mount-key
         :level       level
         :abilities   []
         :spells      spells
         :items       []
         :total-cost  total
         :engine-cost (or (:adjusted-cost parsed-unit) (:cost parsed-unit))}))))

(defn- alliance->state-blob
  "Builds the {:main :reinforcements} draft-state structure from a
  parsed alliance.  The first army's units go to Main; subsequent
  armies (reinforcement chunks) concatenate into Reinforcements."
  [alliance key->row eid->mount-rows conn]
  (let [armies  (vec (:armies alliance))
        unit-fn #(parsed-unit->entry % key->row eid->mount-rows conn)]
    {:main           (vec (keep unit-fn (:units (first armies))))
     :reinforcements (vec (mapcat #(keep unit-fn (:units %)) (rest armies)))}))

(defn- collect-game-unit-keys
  "Every non-blank engine unit key referenced by either alliance in a
  single parsed game."
  [parsed]
  (->> (:alliances parsed)
       (mapcat :armies)
       (mapcat :units)
       (keep :key)
       (remove empty?)
       set))

(defn- resolve-units-for-game
  "Builds the `key → unit-row` map covering every unit key (and its
  successive prefix fallbacks) referenced by one parsed game."
  [datalog-connection parsed]
  (let [keys     (collect-game-unit-keys parsed)
        all-keys (into keys (mapcat parsed-key-prefixes) keys)]
    (if (empty? all-keys)
      {}
      (->> (db/units-by-keys datalog-connection all-keys)
           (into {} (map (juxt :key identity)))))))

(defn- resolve-subfaction-for-alliance
  "Returns the subfaction row referenced by the alliance's parser
  `faction_key`, or nil. The parent race lives at `:faction-eid` on the
  returned row."
  [datalog-connection alliance]
  (when-let [fk (:faction-key alliance)]
    (first (db/subfactions-by-keys datalog-connection [fk]))))

(defn- build-and-persist-draft
  "Creates a draft matching that of the specified player in a game."
  [dependencies {:keys [parsed alliance player-sub round-num game-num
                        tournament game-modes uploaded-by-sub _now]}]
  (let [datalog-conn  (:datalog-connection dependencies)
        subfaction    (resolve-subfaction-for-alliance datalog-conn alliance)
        faction-eid   (:faction-eid subfaction)
        game-mode-eid (:eid (pick-game-mode game-modes (:victory-condition parsed)))]
    (when (and faction-eid game-mode-eid player-sub)
      (let [key->row        (resolve-units-for-game datalog-conn parsed)
            ;; Mount detection must consult the engine-emitted keys (which carry
            ;; the `..._great_taurus` suffix), not the resolved-row keys — the
            ;; mounted variants aren't standalone unit rows, so only their
            ;; un-mounted prefix lives in `key->row`.
            parsed-keys     (collect-game-unit-keys parsed)
            mount-needing   (->> (vals key->row)
                                 (filter (fn [row] (some #(mount-suffix (:key row) %)
                                                         parsed-keys)))
                                 (map :eid)
                                 distinct)
            eid->mount-rows (into {}
                                  (map (fn [eid] [eid (db/mounts-for-unit datalog-conn eid)]))
                                  mount-needing)
            state-blob      (alliance->state-blob alliance key->row eid->mount-rows datalog-conn)
            draft-eid       (random-uuid)
            draft-name      (format "%s R%d G%d"
                                    (:name tournament)
                                    (inc round-num)
                                    (inc game-num))
            entries         (concat
                             (map-indexed
                              (fn [i e] (assoc e :section :main :ordinal i))
                              (:main state-blob))
                             (map-indexed
                              (fn [i e] (assoc e :section :reinforcements :ordinal i))
                              (:reinforcements state-blob)))]
        (db/create-draft! datalog-conn
                          {:eid            draft-eid
                           :name           draft-name
                           :game-mode-eid  game-mode-eid
                           :faction-eid    faction-eid
                           :player-sub     player-sub
                           :created-by-sub uploaded-by-sub})
        (db/add-entries! datalog-conn draft-eid entries)
        draft-eid))))

(defn- build-and-persist-drafts-for-game
  "Returns {:player-one-draft-eid :player-two-draft-eid}; either side
  may be nil when the parsed alliance doesn't resolve to a seeded
  faction or when the match has no opponent yet (bye)."
  [dependencies {:keys [match parsed round-num game-num tournament game-modes
                        uploaded-by-sub now]}]
  (let [[p1 p2] (alliance-sides parsed match uploaded-by-sub)
        build   (fn [{:keys [player-sub alliance]}]
                  (build-and-persist-draft dependencies
                                           {:parsed          parsed
                                            :alliance        alliance
                                            :player-sub      player-sub
                                            :round-num       round-num
                                            :game-num        game-num
                                            :tournament      tournament
                                            :game-modes      game-modes
                                            :uploaded-by-sub uploaded-by-sub
                                            :now             now}))]
    {:player-one-draft-eid (build p1)
     :player-two-draft-eid (build p2)}))

(defn record-game-from-parsed
  "Records a single game in a tournament match from one already-parsed
  replay plus a declared winner. Used by the player-console submitting
  step where games are submitted one at a time.

  Persists a `replay` row, builds and persists per-side drafts, creates
  the next `match_game` row, and updates the parent match if the series
  is now clinched.

  Submission shape is `schema/record-game-submission-spec`. Shape errors
  return :type :match-record/error; contextual errors (match not found,
  match complete, winner not in match) likewise short-circuit. No DB
  writes on any error path."
  [dependencies match-eid {:keys [winner-sub source-name uploaded-by-sub parsed] :as submission}]
  (if-let [shape-error (schema.contract/explain->message
                        (m/explain schema/record-game-submission-spec submission))]
    (short-circuit-error shape-error)
    (let [match (db/match-by-eid (:datalog-connection dependencies) match-eid)]
      (cond
        (nil? match)
        (short-circuit-error "Match not found.")

        (= "complete" (:status match))
        (short-circuit-error "Match is already complete.")

        (not (valid-winner? match winner-sub))
        (short-circuit-error "Declared winner must match one of the match's players.")

        :else
        (let [conn           (:datalog-connection dependencies)
              existing-games (db/games-for-match conn match-eid)
              game-index     (count existing-games)]
          (if (>= game-index (:format match))
            (short-circuit-error (format "Match already has its maximum %d games." (:format match)))
            (let [tournament (db/tournament-by-eid conn (:tournament-eid match))
                  game-modes (db/game-modes-for-game (:datalog-connection dependencies)
                                                     (:game-eid tournament))
                  now        (Instant/now)
                  replay     (persist-replay dependencies
                                             {:parsed          parsed
                                              :source-name     source-name
                                              :uploaded-by-sub uploaded-by-sub})
                  {:keys [player-one-draft-eid player-two-draft-eid]}
                  (build-and-persist-drafts-for-game
                   dependencies
                   {:match           match
                    :parsed          parsed
                    :round-num       (:round-index match)
                    :game-num        game-index
                    :tournament      tournament
                    :game-modes      game-modes
                    :uploaded-by-sub uploaded-by-sub
                    :now             now})
                  stored     (db/create-game!
                              conn match-eid game-index winner-sub
                              {:replay-eid                    (:eid replay)
                               :uploader-local-alliance-index (:uploader-local-alliance-index replay)
                               :player-one-draft-eid          player-one-draft-eid
                               :player-two-draft-eid          player-two-draft-eid})
                  all-games  (conj (mapv #(select-keys % [:winner-sub]) existing-games)
                                   {:winner-sub winner-sub})
                  clincher   (rules.tournament/check-match-complete all-games (:format match))]
              (when clincher
              ;; Route through the public domain function so standings get
              ;; recalculated and tournament-complete check fires — going
              ;; straight to db/update-match-result here would leave the
              ;; state blob stale.
                (handlers.tournament/update-match-result dependencies match-eid clincher))
              (cond-> {:type            :match-record/game-recorded
                       :match-eid       match-eid
                       :game            stored
                       :game-index      game-index
                       :winner-sub      winner-sub
                       :match-complete? (boolean clincher)}
                clincher (assoc :match-winner clincher)))))))))
