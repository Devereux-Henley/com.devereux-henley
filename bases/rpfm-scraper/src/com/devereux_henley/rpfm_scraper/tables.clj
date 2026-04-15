(ns com.devereux-henley.rpfm-scraper.tables
  "Builders for fast lookup maps keyed by table primary key.
  Input `rows` sequences come from rpfm/parse-rpfm-table or parse-loc-file.")

(defn build-armour-map
  "key → armour_value (int)"
  [rows]
  (into {} (map (juxt #(get % "key") #(get % "armour_value"))) rows))

(defn build-entity-map
  "key → {hit_points, run_speed, fly_speed, size}"
  [rows]
  (into {}
        (map (fn [r]
               [(get r "key")
                {:hit_points (get r "hit_points")
                 :run_speed  (get r "run_speed")
                 :fly_speed  (get r "fly_speed")
                 :size       (get r "size")}]))
        rows))

(defn build-mount-entity-map
  "mount key → battle_entity key, from mounts_tables."
  [rows]
  (into {} (map (juxt #(get % "key") #(get % "entity"))) rows))

(defn build-engine-entity-map
  "engine key → battle_entity key, from battlefield_engines_tables."
  [rows]
  (into {} (map (juxt #(get % "key") #(get % "battle_entity"))) rows))

(defn build-melee-weapon-map
  "key → {damage, ap_damage, bonus_v_large, bonus_v_infantry, is_magical,
  ignition_amount, contact_phase}"
  [rows]
  (into {}
        (map (fn [r]
               [(get r "key")
                {:damage           (get r "damage")
                 :ap_damage        (get r "ap_damage")
                 :bonus_v_large    (or (get r "bonus_v_large") 0)
                 :bonus_v_infantry (or (get r "bonus_v_infantry") 0)
                 :is_magical       (get r "is_magical")
                 :ignition_amount  (or (get r "ignition_amount") 0)
                 :contact_phase    (get r "contact_phase")}]))
        rows))

(defn build-missile-weapon-map
  "key → default_projectile key"
  [rows]
  (into {} (map (juxt #(get % "key") #(get % "default_projectile"))) rows))

(defn build-projectile-map
  "key → {effective_range, damage, ap_damage, is_magical, ignition_amount,
  contact_stat_effect, overhead_stat_effect}"
  [rows]
  (into {}
        (map (fn [r]
               [(get r "key")
                {:effective_range      (get r "effective_range")
                 :damage               (get r "damage")
                 :ap_damage            (get r "ap_damage")
                 :is_magical           (get r "is_magical")
                 :ignition_amount      (or (get r "ignition_amount") 0)
                 :contact_stat_effect  (get r "contact_stat_effect")
                 :overhead_stat_effect (get r "overhead_stat_effect")}]))
        rows))

(defn build-attribute-group-map
  "attribute_group key → sorted vector of attribute keys, from
  unit_attributes_to_groups_junctions_tables."
  [rows]
  (reduce (fn [m r]
            (let [group (get r "attribute_group")
                  attr  (get r "attribute")]
              (if (and group attr)
                (update m group (fnil conj []) attr)
                m)))
          {}
          rows))

(def contact-phase-slug-map
  "Maps CA `contact_phase` / `contact_stat_effect` / `overhead_stat_effect`
  keys (as found in melee_weapons_tables and projectiles_tables) to the
  kebab-case modifier slug used by the template (matches
  asset/icon/stat/<slug>.png). Extend as new DLCs introduce new effects."
  {"wh_main_unit_contact_poison"                 "poison"
   "wh2_dlc14_unit_contact_poisoned_wind"        "poison"
   "wh2_dlc14_unit_contact_poisoned_wind_alt"    "poison"
   "wh2_dlc09_unit_contact_shieldbreaker"        "armour-break"
   "wh_dlc06_unit_contact_sundered_armour"       "armour-break"
   "wh2_dlc12_unit_contact_sundered_armour"      "armour-break"
   "wh2_dlc12_unit_contact_flammable"            "flammable"
   "wh_dlc06_unit_contact_blinded"               "blinded"
   "wh3_dlc23_unit_contact_blinded"              "blinded"
   "wh2_dlc17_unit_contact_dazed"                "dazed"
   "wh_dlc08_unit_contact_frostbite"             "frostbite"
   "wh3_dlc25_unit_contact_frostfire"            "frostfire"
   "wh3_main_unit_contact_warpflame"             "flammable"
   "wh3_dlc26_unit_contact_sticky_webs"          "dazed"
   "wh2_dlc12_unit_contact_suppressive_fire"     "suppressive-fire"
   "wh_main_unit_contact_morale"                 "overhead-morale"
   "wh_main_unit_contact_overhead_morale"        "overhead-morale"
   "wh2_main_unit_contact_plague_monk_censer_morale" "overhead-morale"
   "wh_dlc06_unit_contact_discouraged"           "overhead-morale"
   "wh2_dlc15_unit_contact_rattled"              "dazed"
   "wh2_dlc16_unit_contact_weakened"             "blinded"
   "wh2_dlc17_unit_contact_slow_death"           "soulblight"
   "wh3_dlc24_unit_contact_dark_blood"           "blood"
   "wh3_dlc25_unit_contact_erosion"              "flammable"
   "wh3_dlc25_unit_contact_suffocating"          "blinded"
   "wh2_main_unit_contact_weeping_blade"         "poison"
   "wh2_main_unit_contact_souls"                 "soulblight"
   "wh2_pro08_unit_contact_dampen"               "dazed"
   "wh2_dlc11_unit_contact_charmed"              "dazed"
   "wh2_dlc11_unit_contact_disrupted"            "dazed"
   "wh2_dlc11_unit_contact_monstrous_impact"     "dazed"
   "wh2_dlc12_unit_passive_zzzap"                "zzzzap"
   "wh3_dlc26_contact_phase_inevitable_end"      "soulblight"
   "wh3_dlc26_unit_abilities_acid_vomit_debuff"  "flammable"
   "wh3_main_unit_contact_soporific_musk"        "dazed"})

(defn- resolve-contact-slug
  [contact-key]
  (when (and contact-key (seq contact-key))
    (get contact-phase-slug-map contact-key)))

(def ^:private large-sizes #{"large" "very_large" "massive" "ultra"})

(defn- movement-entity-key
  "Pick the battle_entity that actually drives this unit's displayed movement
  speed: engine (chariot/artillery) > mount (cavalry/monstrous mount) > man."
  [r mount-entity-map engine-entity-map]
  (let [engine (get r "engine")
        mount  (get r "mount")]
    (or (when (seq engine) (get engine-entity-map engine))
        (when (seq mount)  (get mount-entity-map mount))
        (get r "man_entity"))))

(defn build-land-unit-map
  "land_unit key → computed stat dict, combining armour/entity/melee/missile lookups."
  [rows armour-map entity-map melee-map missile-wep-map projectile-map
   mount-entity-map engine-entity-map attribute-group-map]
  (into {}
        (map
         (fn [r]
           (let [key       (get r "key")
                 armour    (get armour-map (or (get r "armour") "") 0)
                 man-entity (get entity-map (or (get r "man_entity") "") {})
                 entity-base-hp (or (:hit_points man-entity) 0)
                 bonus-hp       (or (get r "bonus_hit_points") 0)
                 per-entity-hp  (+ entity-base-hp bonus-hp)
                 move-key  (or (movement-entity-key r mount-entity-map engine-entity-map) "")
                 move-entity (get entity-map move-key {})
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
                 melee-mods  (->> [(resolve-contact-slug (:contact_phase melee))]
                                  (filter some?)
                                  distinct
                                  vec)
                 missile-mods (->> [(resolve-contact-slug (:contact_stat_effect proj))
                                    (resolve-contact-slug (:overhead_stat_effect proj))]
                                   (filter some?)
                                   distinct
                                   vec)
                 melee-dmg   (+ (or (:damage melee) 0) (or (:ap_damage melee) 0))
                 missile-dmg (when (seq proj)
                               (+ (or (:damage proj) 0) (or (:ap_damage proj) 0)))
                 move-run    (or (:run_speed move-entity) 0)
                 move-fly    (or (:fly_speed move-entity) 0)
                 run-speed   (max move-run move-fly)
                 size        (or (:size man-entity) "small")
                 is-large    (contains? large-sizes size)
                 attr-group  (get r "attribute_group")
                 attributes  (vec (sort (or (get attribute-group-map attr-group) [])))
                 bonus-infantry (or (:bonus_v_infantry melee) 0)
                 bonus-large    (or (:bonus_v_large melee) 0)]
             [key
              {:bonus_hit_points    (get r "bonus_hit_points")
               :hit_points_per_man  per-entity-hp
               :armour              armour
               :melee_attack        (get r "melee_attack")
               :melee_defence       (get r "melee_defence")
               :morale              (get r "morale")
               :charge_bonus        (get r "charge_bonus")
               :primary_ammo        (or (get r "primary_ammo") 0)
               :melee_attack_types  melee-types
               :melee_modifiers     melee-mods
               :weapon_damage       (:damage melee)
               :weapon_ap_damage    (:ap_damage melee)
               :weapon_strength     (when (seq melee) melee-dmg)
               :bonus_vs_infantry   bonus-infantry
               :bonus_vs_large      bonus-large
               :run_speed           run-speed
               :is_large            is-large
               :missile_range       (when (seq proj) (:effective_range proj))
               :missile_damage      missile-dmg
               :missile_base_damage (when (seq proj) (:damage proj))
               :missile_ap_damage   (when (seq proj) (:ap_damage proj))
               :missile_damage_types missile-types
               :missile_modifiers   missile-mods
               :attributes          attributes}])))
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
