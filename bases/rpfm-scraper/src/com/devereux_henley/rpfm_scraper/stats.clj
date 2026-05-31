(ns com.devereux-henley.rpfm-scraper.stats)

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
               ["health"                (* (or (:hit_points_per_man lu) 0)
                                           (or (:num_men mu) 1))]
               ["barrier"               (or (:barrier mu) 0)]
               ["armor"                 (:armour lu)]
               ["leadership"            (:morale lu)]
               ["speed"                 speed]
               ["melee_attack"          (:melee_attack lu)]
               ["melee_attack_types"    (:melee_attack_types lu)]
               ["melee_modifiers"       (:melee_modifiers lu)]
               ["melee_defence"         (:melee_defence lu)]
               ["weapon_strength"       (:weapon_strength lu)]
               ["weapon_damage"         (:weapon_damage lu)]
               ["weapon_ap_damage"      (:weapon_ap_damage lu)]
               ["bonus_vs_infantry"     (:bonus_vs_infantry lu)]
               ["bonus_vs_large"        (:bonus_vs_large lu)]
               ["charge_bonus"          (:charge_bonus lu)]
               ["ammunition"            (:primary_ammo lu)]
               ["missile_damage_types"  (:missile_damage_types lu)]
               ["missile_modifiers"     (:missile_modifiers lu)]
               ["range"                 (when (:missile_range lu) (:missile_range lu))]
               ["missile_damage"        (:missile_damage lu)]
               ["missile_base_damage"   (:missile_base_damage lu)]
               ["missile_ap_damage"     (:missile_ap_damage lu)]
               ["attributes"            (:attributes lu)]
               ["equipment"             equipment]]]
          (ordered-map pairs))))))
