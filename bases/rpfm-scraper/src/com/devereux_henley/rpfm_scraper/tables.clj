(ns com.devereux-henley.rpfm-scraper.tables
  "Builders for fast lookup maps keyed by table primary key.
  Input `rows` sequences come from rpfm/parse-rpfm-table or parse-loc-file.")

(defn build-armour-map
  "key → armour_value (int)"
  [rows]
  (into {} (map (juxt #(get % "key") #(get % "armour_value"))) rows))

(defn build-entity-map
  "key → {hit_points, run_speed, size}"
  [rows]
  (into {}
        (map (fn [r]
               [(get r "key")
                {:hit_points (get r "hit_points")
                 :run_speed  (get r "run_speed")
                 :size       (get r "size")}]))
        rows))

(defn build-melee-weapon-map
  "key → {damage, ap_damage, is_magical, ignition_amount}"
  [rows]
  (into {}
        (map (fn [r]
               [(get r "key")
                {:damage          (get r "damage")
                 :ap_damage       (get r "ap_damage")
                 :is_magical      (get r "is_magical")
                 :ignition_amount (or (get r "ignition_amount") 0)}]))
        rows))

(defn build-missile-weapon-map
  "key → default_projectile key"
  [rows]
  (into {} (map (juxt #(get % "key") #(get % "default_projectile"))) rows))

(defn build-projectile-map
  "key → {effective_range, damage, ap_damage, is_magical, ignition_amount}"
  [rows]
  (into {}
        (map (fn [r]
               [(get r "key")
                {:effective_range (get r "effective_range")
                 :damage          (get r "damage")
                 :ap_damage       (get r "ap_damage")
                 :is_magical      (get r "is_magical")
                 :ignition_amount (or (get r "ignition_amount") 0)}]))
        rows))

(def ^:private large-sizes #{"large" "very_large" "massive" "ultra"})

(defn build-land-unit-map
  "land_unit key → computed stat dict, combining armour/entity/melee/missile lookups."
  [rows armour-map entity-map melee-map missile-wep-map projectile-map]
  (into {}
        (map
         (fn [r]
           (let [key       (get r "key")
                 armour    (get armour-map (or (get r "armour") "") 0)
                 entity    (get entity-map (or (get r "man_entity") "") {})
                 melee     (get melee-map (or (get r "primary_melee_weapon") "") {})
                 missile-k (or (get r "primary_missile_weapon") "")
                 proj-k    (if (seq missile-k) (get missile-wep-map missile-k "") "")
                 proj      (if (seq proj-k) (get projectile-map proj-k {}) {})
                 melee-types (cond-> []
                               (:is_magical melee) (conj "magical")
                               (pos? (or (:ignition_amount melee) 0)) (conj "flaming"))
                 missile-types (cond-> []
                                 (:is_magical proj) (conj "magical")
                                 (pos? (or (:ignition_amount proj) 0)) (conj "flaming"))
                 melee-dmg   (+ (or (:damage melee) 0) (or (:ap_damage melee) 0))
                 missile-dmg (when (seq proj)
                               (+ (or (:damage proj) 0) (or (:ap_damage proj) 0)))
                 run-speed   (:run_speed entity)
                 size        (or (:size entity) "small")
                 is-large    (contains? large-sizes size)]
             [key
              {:bonus_hit_points    (get r "bonus_hit_points")
               :armour              armour
               :melee_attack        (get r "melee_attack")
               :melee_defence       (get r "melee_defence")
               :morale              (get r "morale")
               :charge_bonus        (get r "charge_bonus")
               :primary_ammo        (or (get r "primary_ammo") 0)
               :melee_attack_types  melee-types
               :weapon_damage       (:damage melee)
               :weapon_ap_damage    (:ap_damage melee)
               :weapon_strength     (when (seq melee) melee-dmg)
               :run_speed           run-speed
               :is_large            is-large
               :missile_range       (when (seq proj) (:effective_range proj))
               :missile_damage      missile-dmg
               :missile_base_damage (when (seq proj) (:damage proj))
               :missile_ap_damage   (when (seq proj) (:ap_damage proj))
               :missile_damage_types missile-types}])))
        rows))

(defn build-main-unit-map
  "unit key → {land_unit, num_men, mp_cost, barrier, is_monstrous}"
  [rows]
  (into {}
        (map (fn [r]
               [(get r "unit")
                {:land_unit    (get r "land_unit")
                 :num_men      (get r "num_men")
                 :mp_cost      (get r "multiplayer_cost")
                 :barrier      (int (or (get r "barrier_health") 0))
                 :is_monstrous (get r "is_monstrous" false)}]))
        rows))

(defn build-special-ability-map
  "ability key → gold_cost (round(additional_melee_cp + additional_missile_cp))"
  [rows]
  (into {}
        (map (fn [r]
               [(get r "key")
                (long (Math/rint (double (+ (or (get r "additional_melee_cp") 0)
                                            (or (get r "additional_missile_cp") 0)))))]))
        rows))

(defn build-agent-subtype-map
  "associated_unit_override (land_unit key) → agent_subtype key.
  Multiple subtypes may share a unit; last-seen wins to match Python."
  [rows]
  (reduce (fn [m r]
            (let [unit-key (or (get r "associated_unit_override") "")]
              (if (seq unit-key)
                (assoc m unit-key (get r "key"))
                m)))
          {}
          rows))

(defn build-equipment-map
  "agent_subtype key → [ancillary_key ...]. Mount ancillaries (keys containing
  '_anc_mount_') are excluded — they are captured by the 'mounts' field."
  [rows]
  (reduce (fn [m r]
            (let [subtype   (get r "agent_subtype")
                  ancillary (get r "ancillary")]
              (if (and ancillary (not (clojure.string/includes? ancillary "_anc_mount_")))
                (update m subtype (fnil conj []) ancillary)
                m)))
          {}
          rows))

(defn build-ancillary-cost-map
  "ancillary key → gold_cost (uniqueness_score field)."
  [rows]
  (into {}
        (keep (fn [r]
                (when-let [score (get r "uniqueness_score")]
                  [(get r "key") score])))
        rows))

(defn build-unit-ability-map
  "ability key → {icon_name, type}"
  [rows]
  (into {}
        (map (fn [r]
               [(get r "key")
                {:icon_name (get r "icon_name")
                 :type      (get r "type")}]))
        rows))

(defn build-ability-loc-maps
  "Returns [name-map tooltip-map] keyed by ability key."
  [loc]
  (let [name-prefix    "unit_abilities_onscreen_name_"
        tooltip-prefix "unit_abilities_tooltip_"
        np             (count name-prefix)
        tp             (count tooltip-prefix)]
    (reduce-kv
     (fn [[names tooltips] k v]
       (cond
         (and k (clojure.string/starts-with? k name-prefix))
         [(assoc names (subs k np) v) tooltips]

         (and k (clojure.string/starts-with? k tooltip-prefix))
         [names (assoc tooltips (subs k tp) v)]

         :else [names tooltips]))
     [{} {}]
     loc)))

(defn build-ability-name-key-map
  "Inverts ability key→name to name→key for legacy-name migration."
  [ability-name-map]
  (reduce-kv (fn [m k v] (assoc m v k)) {} ability-name-map))

(defn resolve-ability-names-to-keys
  "Convert legacy display-name entries to canonical ability keys. Entries
  that aren't display names pass through unchanged."
  [abilities name->key]
  (mapv #(get name->key % %) abilities))
