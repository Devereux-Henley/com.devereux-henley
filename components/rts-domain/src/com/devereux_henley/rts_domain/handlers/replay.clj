(ns com.devereux-henley.rts-domain.handlers.replay
  "Parses uploaded `.replay` files via the Rust `tw-replay-parser` binary and
  binds the parsed result to a tournament match via per-game match_game rows."
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as string]
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.handlers.draft :as handlers.draft]
   [com.devereux-henley.rts-domain.rules.tournament :as rules.tournament]
   [jsonista.core :as jsonista])
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
  "Inserts a `replay` row from a parsed map. Returns the inserted entity
  (with `:id` populated for FK use by match_game)."
  [dependencies {:keys [parsed source-name uploaded-by-sub]}]
  (let [now (Instant/now)]
    (db/create-replay
     (:connection dependencies)
     {:eid                           (random-uuid)
      :match-id-external             (or (:match-id parsed)
                                         source-name
                                         "unknown")
      :played-at                     (or (iso-played-at (:played-at parsed))
                                         (str now))
      :victory-condition             (:victory-condition parsed)
      :parser-format                 (:format parsed)
      :parsed-json                   (jsonista/write-value-as-string parsed)
      :uploader-local-alliance-index (:uploader-local-alliance-index parsed)
      :uploaded-by-sub               uploaded-by-sub
      :created-at                    now
      :updated-at                    now})))

(defn- valid-winner?
  "A declared winner-sub must equal one of the match's player subs."
  [{:keys [player-one-sub player-two-sub]} winner-sub]
  (or (= winner-sub player-one-sub)
      (= winner-sub player-two-sub)))

(defn- short-circuit-error
  [message]
  {:type :match-record/error :message message})

(defn parse-replay-files
  "Parses N uploaded replays without touching the DB. Returns a vector of
  parsed maps in the same order as the input. Used by the modal's Phase 1
  (Step 1 → Step 2) so the client can render parsed drafts in Step 3 before
  any winners are declared."
  [dependencies file-paths]
  (mapv #(parse-replay-file dependencies %) file-paths))

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

(defn- pick-game-mode-eid
  "Picks a game-mode eid from `game-modes` keyed on the parsed
  `victory_condition`. Falls back to the first game-mode (typically
  `Land Battle`) when no mapping matches."
  [game-modes victory-condition]
  (let [target (if (contains? domination-victory-conditions victory-condition)
                 "Domination"
                 "Land Battle")
        chosen (first (filter #(= target (:name %)) game-modes))]
    (:eid (or chosen (first game-modes)))))

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
  [connection parsed]
  (let [keys     (collect-game-unit-keys parsed)
        all-keys (into keys (mapcat parsed-key-prefixes) keys)]
    (if (empty? all-keys)
      {}
      (->> (db/get-units-by-keys connection all-keys)
           (into {} (map (juxt :key identity)))))))

(defn- resolve-subfaction-for-alliance
  "Returns the subfaction row referenced by the alliance's parser
  `faction_key`, or nil. The parent race lives at `:faction-eid` on the
  returned row."
  [connection alliance]
  (when-let [fk (:faction-key alliance)]
    (first (db/get-subfactions-by-keys connection [fk]))))

(defn- build-and-persist-draft
  "Creates one draft (row + state) for the given side of a parsed game.
  Returns the created draft's eid, or nil when the parsed alliance
  can't be resolved to a seeded faction — without that we can't pick a
  faction-eid for the row, so we skip rather than insert a row with a
  bogus FK."
  [dependencies {:keys [parsed alliance player-sub round-num game-num
                        tournament game-modes uploaded-by-sub now]}]
  (let [conn          (:connection dependencies)
        subfaction    (resolve-subfaction-for-alliance conn alliance)
        faction-eid   (:faction-eid subfaction)
        game-mode-eid (pick-game-mode-eid game-modes (:victory-condition parsed))]
    (when (and faction-eid game-mode-eid player-sub)
      (let [key->row        (resolve-units-for-game conn parsed)
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
                                  (map (fn [eid] [eid (db/get-mounts-for-unit conn eid)]))
                                  mount-needing)
            state-blob      (alliance->state-blob alliance key->row eid->mount-rows conn)
            draft-eid       (random-uuid)
            draft-name      (format "%s R%d G%d"
                                    (:name tournament)
                                    (inc round-num)
                                    (inc game-num))]
        (db/create-draft conn
                         {:eid            draft-eid
                          :name           draft-name
                          :game-mode-eid  game-mode-eid
                          :faction-eid    faction-eid
                          :player-sub     player-sub
                          :version        1
                          :created-by-sub uploaded-by-sub
                          :created-at     (str now)
                          :updated-at     (str now)})
        (db/upsert-draft-state conn draft-eid (jsonista/write-value-as-string state-blob))
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

(defn record-match-from-parsed
  "Atomically records a tournament match from N already-parsed replays plus
  per-game declared winners.

  `submission` shape:
    {:games [{:parsed     <map>            ; output of parse-replay-files
              :winner-sub \"sigmar_42\"   ; declared winner for this game
              :source-name \"foo.replay\"  ; optional, fallback for match-id
              }
             …]
     :uploaded-by-sub \"sigmar_42\"}

  Behaviour:
    * Persists a `replay` row per parsed map.
    * Persists a `match_game` row per game, linked to the replay and tagged
      with the uploader's local alliance index from the parsed header.
    * Updates the parent `match` if the series is mathematically clinched.

  Validation errors short-circuit (no DB writes).  Match must be `pending`
  and must not already have any games recorded — re-uploads on a partially
  recorded match are rejected to keep the modal flow simple."
  [dependencies match-eid submission]
  (let [match (db/get-match-by-eid (:connection dependencies) match-eid)]
    (cond
      (nil? match)
      (short-circuit-error "Match not found.")

      (= "complete" (:status match))
      (short-circuit-error "Match is already complete.")

      (seq (db/get-games-for-match (:connection dependencies) match-eid))
      (short-circuit-error "Match already has recorded games.")

      (not (<= 1 (count (:games submission)) (:format match)))
      (short-circuit-error
       (format "A Bo%d match accepts between 1 and %d games; got %d."
               (:format match) (:format match) (count (:games submission))))

      (not (every? #(valid-winner? match (:winner-sub %)) (:games submission)))
      (short-circuit-error "Declared winner must match one of the match's players.")

      :else
      (let [provisional  (mapv (fn [g] {:winner-sub (:winner-sub g)}) (:games submission))
            match-winner (rules.tournament/check-match-complete provisional (:format match))]
        (cond
          (nil? match-winner)
          (short-circuit-error
           (format "Submitted games do not decide the series — no player reached the Bo%d win threshold."
                   (:format match)))

          :else
          (let [conn            (:connection dependencies)
                tournament      (db/get-tournament-by-eid conn (:tournament-eid match))
                game-modes      (db/get-game-modes-for-game conn (:game-eid tournament))
                uploaded-by-sub (:uploaded-by-sub submission)
                now             (Instant/now)
                stored
                (mapv (fn [game-index {:keys [parsed winner-sub source-name]}]
                        (let [replay (persist-replay dependencies
                                                     {:parsed          parsed
                                                      :source-name     source-name
                                                      :uploaded-by-sub uploaded-by-sub})
                              ;; Auto-create one draft per side from the parsed
                              ;; replay so the match_game row can lock both
                              ;; players' lineups. Either eid can come back nil
                              ;; when the alliance's faction_key isn't in the
                              ;; seed; in that case the game records normally
                              ;; with that side's draft slot left empty.
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
                                :now             now})]
                          (db/create-game
                           conn match-eid game-index winner-sub
                           {:replay-eid                    (:eid replay)
                            :uploader-local-alliance-index (:uploader-local-alliance-index replay)
                            :player-one-draft-eid          player-one-draft-eid
                            :player-two-draft-eid          player-two-draft-eid})))
                      (range)
                      (:games submission))]
            (db/update-match-result conn match-eid match-winner)
            {:type       :match-record/recorded
             :match-eid  match-eid
             :games      stored
             :winner-sub match-winner
             :complete?  true}))))))

(defn get-record-context
  "Fetches the data needed to render the post-match modal for a match:
   the match itself plus any already-recorded games (which determines whether
   to surface the recorder UI or a read-only summary)."
  [dependencies match-eid]
  (let [match (db/get-match-by-eid (:connection dependencies) match-eid)]
    (when match
      {:match match
       :games (db/get-games-for-match (:connection dependencies) match-eid)})))
