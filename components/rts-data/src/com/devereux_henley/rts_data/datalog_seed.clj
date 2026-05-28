(ns com.devereux-henley.rts-data.datalog-seed
  "Read pre-built Datalog seed files from the classpath. Each patch lives
  under `rts-data/seed/datalog/<patch-version>/` as one EDN file per entity
  type. The files are produced offline by the dev-only dump tool
  (`development/src/dump_datalog_seed.clj`) and committed alongside the
  SQL seeds.

  This namespace deliberately knows nothing about Datalevin — callers
  pass the returned tx-data to `datalog.contract/transact!` themselves.
  That keeps `rts-data` free of a Datalevin runtime dependency for
  builds that don't use it."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]))

(def seed-files
  "EDN seed files, in transact order. Phase-1 entities (no FKs to other
  game-domain rows) load first; junctions and `:unit-statistics` come
  last so their lookup-ref targets already exist."
  [;; Phase 1: independent
   "patches.edn"
   "games.edn"
   "social-media-platforms.edn"
   "unit-level-cost.edn"
   ;; Phase 2: per-game lookups
   "game-social-links.edn"
   "unit-types.edn"
   "unit-categories.edn"
   "factions.edn"
   "game-modes.edn"
   "lores.edn"
   "spells.edn"
   "abilities.edn"
   "attributes.edn"
   "items.edn"
   "mounts.edn"
   ;; Phase 3: refs into phase 2
   "subfactions.edn"
   "spell-lores.edn"
   ;; Phase 4: units (need factions, unit-types, unit-categories)
   "units.edn"
   ;; Phase 5: junctions hanging off units
   "unit-items.edn"
   "unit-mounts.edn"
   ;; Phase 6: per-patch statline snapshots (point back at unit + patch)
   "unit-statistics.edn"])

(defn- seed-resource
  [patch-version file-name]
  (io/resource (str "rts-data/seed/datalog/" patch-version "/" file-name)))

(defn- read-edn-resource
  [resource]
  (with-open [r (io/reader resource)]
    (edn/read (java.io.PushbackReader. r))))

(defn load-file-tx
  "Read a single seed file for `patch-version`. Returns the EDN-decoded
  tx-data vector, or nil if the file is missing (some entity types may be
  empty for a given patch — the dump tool skips empty files)."
  [patch-version file-name]
  (when-let [r (seed-resource patch-version file-name)]
    (read-edn-resource r)))

(defn load-all
  "Return `[[file-name tx-data] …]` for every present seed file under
  `patch-version`, in transact order. Callers transact one batch per file
  so progress logging and error reporting can stay per-entity."
  [patch-version]
  (into []
        (keep (fn [file-name]
                (when-let [tx (load-file-tx patch-version file-name)]
                  [file-name tx])))
        seed-files))

(defn available-patches
  "Scan the classpath under `rts-data/seed/datalog/` and return the set of
  patch directories that have at least one seed file. Useful for `bd`
  tooling or a `/dev/seed` UI later. Quietly returns `#{}` when no seeds
  are present."
  []
  (let [root (io/resource "rts-data/seed/datalog/")]
    (if (and root (= "file" (.getProtocol root)))
      (into (sorted-set)
            (comp (filter #(.isDirectory ^java.io.File %))
                  (map #(.getName ^java.io.File %)))
            (.listFiles (io/file root)))
      ;; When running from an uberjar the directory listing is opaque;
      ;; callers that need this should pass the patch version explicitly.
      (sorted-set))))

(defn ensure-patch-version
  "Throw a friendly error if no seed files exist for the requested patch.
  Use at REPL entry points so a typo doesn't silently transact nothing."
  [patch-version]
  (when (empty? (load-all patch-version))
    (let [available (available-patches)]
      (throw
       (ex-info
        (str "No Datalog seed files found for patch " (pr-str patch-version)
             (when (seq available)
               (str " (available: " (str/join ", " available) ")")))
        {:patch-version patch-version
         :available     available})))))
