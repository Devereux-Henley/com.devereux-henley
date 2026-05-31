(ns split-authoring-seed
  "One-time dev transform: derive the curated authoring EDN
  (`seed/authoring/<version>/`) from the committed merged seed
  (`seed/datalog/<version>/`). See docs/rpfm-scraper/edn-seed-pipeline.md.

  Curated entities are copied verbatim. Hybrid entities (units, abilities,
  spells) keep only their identity + curated fields; the RPFM-derived fields
  are dropped because the scraper regenerates them. Generated-only files
  (items, mounts, subfactions, junctions, statistics, unit-level-cost) and
  build metadata (patches) are not authored, so they are not emitted here."
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.pprint :as pp]))

(def ^:private datalog-root
  "components/rts-data/resources/rts-data/seed/datalog")

(def ^:private authoring-root
  "components/rts-data/resources/rts-data/seed/authoring")

(def ^:private curated-files
  "Fully-curated seed files, copied verbatim into the authoring tree."
  ["games.edn" "social-media-platforms.edn" "game-social-links.edn"
   "unit-types.edn" "unit-categories.edn" "factions.edn" "game-modes.edn"
   "lores.edn" "attributes.edn" "spell-lores.edn" "unit-level-cost.edn"])

(def ^:private hybrid-keep
  "Hybrid seed files → the ordered identity + curated keys to retain. Every
  other key on the row is RPFM-derived and dropped (the scraper regenerates
  it and the merge re-attaches it by eid)."
  {"spells.edn"    [:spell/eid :spell/key :spell/name :spell/description
                    :spell/spell-type :spell/mana-cost :spell/game]
   "abilities.edn" [:ability/eid :ability/key :ability/ability-type :ability/description :ability/game]
   "units.edn"     [:unit/eid :unit/name :unit/description :unit/faction
                    :unit/unit-type :unit/unit-category :unit/is-unique :unit/game]})

(defn- read-edn
  [file]
  (edn/read-string (slurp file)))

(defn- select-ordered
  "Like `select-keys` but yields an array-map in `ks` order so pprint output
  is stable and namespaced-map-friendly. Absent keys are skipped."
  [m ks]
  (reduce (fn [acc k] (if (contains? m k) (assoc acc k (get m k)) acc))
          (array-map) ks))

(defn- spit-edn!
  [file data]
  (.mkdirs (.getParentFile file))
  (with-open [w (io/writer file)]
    (binding [*out* w] (pp/pprint data)))
  (println (format "  %-28s %d rows" (.getName file) (count data))))

(def ^:private statistics-curated-keys
  "The curated subset of a unit-statistics row: the owning unit ref plus the
  draftable lists RPFM can't derive. The numeric statline is regenerated."
  [:unit-statistics/unit :unit-statistics/abilities :unit-statistics/draftable-spells])

(defn- curated-statistics
  "Project unit-statistics rows to their curated subset, keeping only rows that
  actually carry abilities or draftable-spells."
  [rows]
  (into []
        (comp (filter #(or (:unit-statistics/abilities %)
                           (:unit-statistics/draftable-spells %)))
              (map #(select-ordered % statistics-curated-keys)))
        rows))

(defn split!
  "Write `seed/authoring/<version>/` from `seed/datalog/<version>/`."
  [version]
  (let [src  (io/file datalog-root version)
        dest (io/file authoring-root version)]
    (println (format "Splitting authoring EDN for patch %s → %s" version (.getPath dest)))
    (doseq [file-name curated-files
            :let      [in (io/file src file-name)]
            :when     (.exists in)]
      (spit-edn! (io/file dest file-name) (read-edn in)))
    (doseq [[file-name ks] hybrid-keep
            :let           [in (io/file src file-name)]
            :when          (.exists in)]
      (spit-edn! (io/file dest file-name)
                 (mapv #(select-ordered % ks) (read-edn in))))
    (let [stats-in (io/file src "unit-statistics.edn")]
      (when (.exists stats-in)
        (spit-edn! (io/file dest "unit-statistics.edn")
                   (curated-statistics (read-edn stats-in)))))
    (println "Done.")))

(comment
  (split! "8.0"))
