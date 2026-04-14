(ns com.devereux-henley.rpfm-scraper.spells-seed
  (:require
   [clojure.string :as str]))

(def spell-update-re
  #"(\(\d+,\s*'[0-9a-f\-]+',\s*'([^']+)',\s*'(?:[^']|'')*',\s*'(?:[^']|'')*',\s*'[^']+',\s*\d+,\s*)(\d+)(,\s*\d+,\s*\d+,\s*')")

(defn update-spell-seed-file
  "Rewrite gold_cost for every spell row in seed-spells.sql using the
  RPFM unit_special_abilities_tables mapping."
  [filepath special-ability-map]
  (let [content (slurp filepath)]
    (if-not (str/includes? content "cost")
      (do (binding [*out* *err*]
            (println "  seed-spells.sql does not have cost column — skipping spell update"))
          content)
      (let [not-found (atom [])
            found     (atom 0)
            replacer
            (fn [g]
              (let [prefix-str (nth g 1)
                    spell-key  (nth g 2)
                    suffix     (nth g 4)
                    gold-cost  (get special-ability-map spell-key)]
                (if (nil? gold-cost)
                  (do (swap! not-found conj spell-key)
                      (str prefix-str "0" suffix))
                  (do (swap! found inc)
                      (str prefix-str gold-cost suffix)))))
            new-content (str/replace content spell-update-re replacer)]
        (binding [*out* *err*]
          (println (format "  Updated %d spell gold costs, %d not found"
                           @found (count @not-found))))
        new-content))))
