(ns com.devereux-henley.rts-data.datalog-seed
  "Read pre-built Datalog seed files from the classpath. Each patch lives
  under `rts-data/seed/datalog/<patch-version>/` as one EDN file per entity
  type. The files are produced offline by the `rpfm-scraper` base (curated
  authoring EDN ⊕ RPFM data) and committed.

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
   "item-abilities.edn" ; before items.edn — :item/abilities refs these rows
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
  empty for a given patch — the scraper skips empty files)."
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

(def ^:private seed-root "rts-data/seed/datalog/")

(defn- patches-on-disk
  "Patch directory names directly under a `file:` seed root."
  [^java.io.File root]
  (into (sorted-set)
        (comp (filter #(.isDirectory ^java.io.File %))
              (map #(.getName ^java.io.File %)))
        (.listFiles root)))

(defn- patches-in-jar
  "Patch directory names enumerated from the jar that backs a `jar:` seed
  root. A patch counts as present once any entry nests below its directory
  (e.g. `…/datalog/8.0/units.edn` yields `8.0`)."
  [^java.net.JarURLConnection conn]
  (with-open [jar (.getJarFile conn)]
    (into (sorted-set)
          (keep (fn [^java.util.jar.JarEntry entry]
                  (let [name (.getName entry)]
                    (when (str/starts-with? name seed-root)
                      (let [rest  (subs name (count seed-root))
                            slash (str/index-of rest "/")]
                        (when (pos? (long (or slash -1)))
                          (subs rest 0 slash)))))))
          (enumeration-seq (.entries jar)))))

(defn available-patches
  "Scan the classpath under `rts-data/seed/datalog/` and return the set of
  patch directories that have at least one seed file. Resolves both a
  `file:` root (dev/test classpath) and a `jar:` root (the packaged uberjar,
  where the directory listing is enumerated from the jar's entry table).
  Useful for `bd` tooling or a `/dev/seed` UI later. Quietly returns `#{}`
  when no seeds are present."
  []
  (let [root (io/resource seed-root)]
    (case (some-> root .getProtocol)
      "file" (patches-on-disk (io/file root))
      "jar"  (let [conn (.openConnection root)]
               (if (instance? java.net.JarURLConnection conn)
                 (patches-in-jar conn)
                 (sorted-set)))
      (sorted-set))))

(defn missing-seed-files
  "The `seed-files` that have no resource under `patch-version`, in transact
  order. Empty when the dump is complete. Probes resource presence only — no
  EDN is parsed — so it is cheap to call before `load-all`."
  [patch-version]
  (into [] (remove #(seed-resource patch-version %)) seed-files))

(defn ensure-patch-version
  "Throw a friendly error unless every declared seed file exists for
  `patch-version`. Probes resource presence only — no EDN is parsed — so a
  reseed doesn't read the corpus twice, and a partial dump (some files written,
  then a crash) is rejected instead of silently transacting an incomplete
  graph. Use at REPL entry points so a typo or half-finished dump doesn't
  silently transact nothing — or worse, part of the graph."
  [patch-version]
  (let [missing (missing-seed-files patch-version)]
    (when (seq missing)
      (let [none?     (= (count missing) (count seed-files))
            available (available-patches)]
        (throw
         (ex-info
          (if none?
            (str "No Datalog seed files found for patch " (pr-str patch-version)
                 (when (seq available)
                   (str " (available: " (str/join ", " available) ")")))
            (str "Incomplete Datalog seed for patch " (pr-str patch-version)
                 " — missing " (count missing) " of " (count seed-files)
                 " file(s): " (str/join ", " missing)))
          {:patch-version patch-version
           :missing       missing
           :available     available}))))))
