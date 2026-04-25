(ns com.devereux-henley.rts-domain.handlers.replay
  (:require
   [clojure.java.shell :as shell]
   [clojure.string :as string]
   [com.devereux-henley.rts-data-access.contract :as db]
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

(defn- tag-replay
  [row parsed]
  (when row
    (-> row
        (assoc :type          :replay/replay
               :alliances     (:alliances parsed)
               :played-at     (:played-at row)
               :uploaded-at   (str (:created-at row)))
        (dissoc :parsed-json :created-at :updated-at :deleted-at :id :version))))

(defn- hydrate-replay
  "Takes a raw replay row from the DB, parses its JSON blob, and returns the
  domain resource shape."
  [row]
  (when row
    (let [parsed (jsonista/read-value (:parsed-json row) json-mapper)]
      (tag-replay row parsed))))

(defn get-replay-by-eid
  [dependencies eid]
  (hydrate-replay (db/get-replay-by-eid (:connection dependencies) eid)))

(defn get-replays-for-uploader
  [dependencies uploader-sub]
  (mapv hydrate-replay (db/get-replays-for-uploader (:connection dependencies) uploader-sub)))

(defn create-replay
  "Parses a replay file, persists the extracted metadata, and returns the
  created domain resource.

  `specification` keys:
    :eid              — uuid to assign
    :file-path        — absolute path to the .replay file on disk
    :uploaded-by-sub  — auth subject of the uploader"
  [dependencies {:keys [eid file-path uploaded-by-sub]}]
  (let [parsed    (parse-replay-file dependencies file-path)
        match-id  (:match-id parsed)
        played-at (or (iso-played-at (:played-at parsed))
                      (str (Instant/now)))
        now       (Instant/now)
        row       (db/create-replay
                   (:connection dependencies)
                   {:eid               eid
                    :match-id          match-id
                    :played-at         played-at
                    :victory-condition (:victory-condition parsed)
                    :parser-format     (:format parsed)
                    :parsed-json       (jsonista/write-value-as-string parsed)
                    :uploaded-by-sub   uploaded-by-sub
                    :version           1
                    :created-at        now
                    :updated-at        now})]
    (hydrate-replay row)))

(defn declare-winner
  "Updates the user-declared winning alliance for the replay. `winning-idx`
  may be 0, 1, -1 (draw), or nil (clear)."
  [dependencies eid winning-idx]
  (hydrate-replay
   (db/update-replay-winner (:connection dependencies) eid winning-idx)))
