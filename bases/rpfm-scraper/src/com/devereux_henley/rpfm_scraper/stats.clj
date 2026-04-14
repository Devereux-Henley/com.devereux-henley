(ns com.devereux-henley.rpfm-scraper.stats
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [com.devereux-henley.rpfm-scraper.name-match :as nm]
   [com.devereux-henley.rpfm-scraper.tables :as tables]))

(defn- ordered-map
  "Build a java.util.LinkedHashMap from a seq of [k v] pairs, dropping nil
  values. data.json serializes LinkedHashMap in insertion order, matching
  Python's json.dumps on an insertion-ordered dict."
  ^java.util.LinkedHashMap [pairs]
  (let [m (java.util.LinkedHashMap.)]
    (doseq [[k v] pairs :when (some? v)]
      (.put m k v))
    m))

(defn- round-int
  "Banker's rounding (round half to even) to match Python's built-in round()."
  [x]
  (when (some? x) (int (Math/rint (double x)))))

(defn extract-stats
  "Compute the stats map for a unit-key. Returns nil when RPFM has no data
  for the unit."
  [unit-key main-unit-map land-unit-stats
   agent-subtype-map equipment-map ancillary-cost-map]
  (when-let [mu (get main-unit-map unit-key)]
    (let [land-unit-key (:land_unit mu)]
      (when-let [lu (get land-unit-stats land-unit-key)]
        (let [speed (round-int (* 10 (or (:run_speed lu) 0)))
              equipment
              (when (and agent-subtype-map equipment-map)
                (when-let [subtype (get agent-subtype-map land-unit-key)]
                  (when-let [items (get equipment-map subtype)]
                    (mapv (fn [k]
                            (ordered-map
                             [["key" k]
                              ["cost" (get ancillary-cost-map k)]]))
                          items))))
              pairs
              [["cost"                  (:mp_cost mu)]
               ["is_large"              (or (:is_large lu) (:is_monstrous mu false))]
               ["unit_size"             (:num_men mu)]
               ["health"                (:bonus_hit_points lu)]
               ["barrier"               (or (:barrier mu) 0)]
               ["armor"                 (:armour lu)]
               ["leadership"            (:morale lu)]
               ["speed"                 speed]
               ["melee_attack"          (:melee_attack lu)]
               ["melee_attack_types"    (:melee_attack_types lu)]
               ["melee_defence"         (:melee_defence lu)]
               ["weapon_strength"       (:weapon_strength lu)]
               ["weapon_damage"         (:weapon_damage lu)]
               ["weapon_ap_damage"      (:weapon_ap_damage lu)]
               ["charge_bonus"          (:charge_bonus lu)]
               ["ammunition"            (:primary_ammo lu)]
               ["missile_damage_types"  (:missile_damage_types lu)]
               ["range"                 (when (:missile_range lu) (:missile_range lu))]
               ["missile_damage"        (:missile_damage lu)]
               ["missile_base_damage"   (:missile_base_damage lu)]
               ["missile_ap_damage"     (:missile_ap_damage lu)]
               ["equipment"             equipment]]]
          (ordered-map pairs))))))

(defn- apply-preserved
  "Copy preserved fields from the old-stats hash into the new LinkedHashMap
  in the canonical order: abilities, draftable-spells, mounts, is_unique,
  equipment (fallback). Abilities may still be stored as legacy display
  names; resolve them to canonical keys when `ability-name->key` is given."
  [^java.util.LinkedHashMap new-stats old-stats ability-name->key]
  (when (contains? old-stats "abilities")
    (let [abilities (get old-stats "abilities")
          resolved  (if ability-name->key
                      (tables/resolve-ability-names-to-keys abilities ability-name->key)
                      abilities)]
      (.put new-stats "abilities" resolved)))
  (doseq [k ["draftable-spells" "mounts" "is_unique"]]
    (when (contains? old-stats k)
      (.put new-stats k (get old-stats k))))
  (when (and (not (.containsKey new-stats "equipment"))
             (contains? old-stats "equipment"))
    (.put new-stats "equipment" (get old-stats "equipment")))
  new-stats)

(def stats-block-re
  #"(\(\d+,\s*'[0-9a-f\-]+',\s*'((?:[^']|'')*)',\s*'(?:[^']|'')*',\s*\n\s*\d+,\s*\d+,\s*\d+,\s*\d+,\s*')(\{[^']*(?:''[^']*)*\})(')")

(defn update-unit-seed-file
  "Rewrite the stats blob for every INSERT row in `filepath` using RPFM data.
  Returns the new file contents as a string and logs per-faction counts to
  stderr."
  [filepath faction-name faction-prefixes name-index main-unit-map land-unit-stats
   agent-subtype-map equipment-map ancillary-cost-map ability-name->key]
  (let [content   (slurp filepath)
        not-found (atom [])
        no-data   (atom [])
        found     (atom 0)
        replacer
        (fn [m]
          (let [prefix-str    (nth m 1)
                raw-name      (str/replace (nth m 2) "''" "'")
                old-stats-str (nth m 3)
                suffix        (nth m 4)
                [unit-key _lu] (nm/find-unit-key raw-name faction-prefixes name-index)]
            (cond
              (nil? unit-key)
              (do (swap! not-found conj raw-name) (nth m 0))

              :else
              (if-let [^java.util.LinkedHashMap new-stats
                       (extract-stats unit-key main-unit-map land-unit-stats
                                      agent-subtype-map equipment-map ancillary-cost-map)]
                (let [old-json  (str/replace old-stats-str "''" "'")
                      old-stats (try (json/read-str old-json) (catch Exception _ {}))]
                  (apply-preserved new-stats old-stats ability-name->key)
                  (swap! found inc)
                  (str prefix-str
                       (-> (json/write-str new-stats
                                           :escape-slash false
                                           :escape-js-separators false)
                           (str/replace "'" "''"))
                       suffix))
                (do (swap! no-data conj raw-name) (nth m 0))))))
        new-content (str/replace content stats-block-re replacer)]
    (binding [*out* *err*]
      (when (seq @not-found)
        (println (format "  [%s] %d units not matched in loc: %s%s"
                         faction-name (count @not-found)
                         (pr-str (take 5 @not-found))
                         (if (> (count @not-found) 5) "..." ""))))
      (when (seq @no-data)
        (println (format "  [%s] %d units matched but no game data: %s"
                         faction-name (count @no-data) (pr-str (take 5 @no-data)))))
      (println (format "  [%s] updated %d units" faction-name @found)))
    new-content))
