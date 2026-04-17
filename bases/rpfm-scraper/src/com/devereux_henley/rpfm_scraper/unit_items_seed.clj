(ns com.devereux-henley.rpfm-scraper.unit-items-seed
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.devereux-henley.rpfm-scraper.name-match :as nm]
   [com.devereux-henley.rpfm-scraper.overrides :as overrides]))

(def ^:private seed-author "f0ce7395-a57f-41e9-ade0-fd13bafc058f")

(def ^:private unit-seed-id-name-re
  #"\(\s*(\d+),\s*'([0-9a-f\-]+)',\s*'((?:[^']|'')*)',")

(defn build-unit-seed-id-map
  "Walk every seed-<faction>-units.sql file and return {[display-name faction-slug] unit-id}."
  [seed-dir]
  (let [files (->> (.list (io/file seed-dir))
                   (filter #(and (str/starts-with? % "seed-")
                                 (str/ends-with? % "-units.sql")))
                   sort)]
    (reduce
     (fn [m filename]
       (let [faction (subs filename (count "seed-") (- (count filename) (count "-units.sql")))
             content (slurp (io/file seed-dir filename))]
         (reduce
          (fn [m2 match]
            (let [unit-id (Long/parseLong (nth match 1))
                  name    (str/replace (nth match 3) "''" "'")]
              (assoc m2 [name faction] unit-id)))
          m
          (re-seq unit-seed-id-name-re content))))
     {}
     files)))

(defn generate-unit-item-seed
  "Full seed-unit-items.sql regen linking legendary lords/heroes to their
  pre-assigned items. Iterates factions in sorted order for stable output."
  [unit-id-map name-index main-unit-map agent-subtype-map equipment-map item-key->id]
  (let [seen (atom #{})
        rows (atom [])]
    (doseq [[faction-name faction-prefixes] (sort-by key overrides/faction-key-map)
            [[name faction] unit-id]        unit-id-map
            :when                           (= faction faction-name)]
      (let [[unit-key _] (nm/find-unit-key name faction-prefixes name-index)]
        (when unit-key
          (when-let [mu (get main-unit-map unit-key)]
            (let [land-unit-key (:land_unit mu)]
              (when-let [agent-subtype (get agent-subtype-map land-unit-key)]
                (when-let [items (get equipment-map agent-subtype)]
                  (doseq [item-key items]
                    (when-let [item-id (get item-key->id item-key)]
                      (let [pair [unit-id item-id]]
                        (when-not (contains? @seen pair)
                          (swap! seen conj pair)
                          (swap! rows conj [unit-id item-id]))))))))))))
    (let [rows (sort @rows)
          n    (count rows)]
      (if (zero? n)
        (str "INSERT OR IGNORE INTO unit_item(id, unit_id, item_id,"
             " version, created_by_sub, created_at, updated_at, deleted_at)\n"
             "VALUES\n"
             "  -- no rows generated\n;\n")
        (let [header  ["INSERT OR IGNORE INTO unit_item(id, unit_id, item_id, version, created_by_sub, created_at, updated_at, deleted_at)"
                       "VALUES"]
              indexed (map-indexed (fn [i r] [(inc i) r]) rows)
              row-sql (fn [[idx [unit-id item-id]]]
                        (let [comma (if (< idx n) "," ";")]
                          (format "  (%d, %d, %d, 1, '%s', STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), null)%s"
                                  idx unit-id item-id seed-author comma)))]
          (str (str/join "\n" (concat header (map row-sql indexed))) "\n"))))))
