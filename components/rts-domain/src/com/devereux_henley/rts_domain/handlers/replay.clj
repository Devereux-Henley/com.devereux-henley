(ns com.devereux-henley.rts-domain.handlers.replay
  "Parses uploaded `.replay` files via the Rust `tw-replay-parser` binary and
  binds the parsed result to a tournament match via per-game match_game rows."
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as string]
   [com.devereux-henley.rts-data-access.contract :as db]
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
          (let [stored (mapv (fn [game-index {:keys [parsed winner-sub source-name]}]
                               (let [replay (persist-replay dependencies
                                                            {:parsed          parsed
                                                             :source-name     source-name
                                                             :uploaded-by-sub (:uploaded-by-sub submission)})]
                                 (db/create-game
                                  (:connection dependencies)
                                  match-eid game-index winner-sub
                                  {:replay-eid                    (:eid replay)
                                   :uploader-local-alliance-index (:uploader-local-alliance-index replay)})))
                             (range)
                             (:games submission))]
            (db/update-match-result (:connection dependencies) match-eid match-winner)
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
