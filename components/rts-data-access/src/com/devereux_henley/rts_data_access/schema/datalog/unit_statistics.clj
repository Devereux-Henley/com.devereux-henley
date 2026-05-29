(ns com.devereux-henley.rts-data-access.schema.datalog.unit-statistics
  "Datalevin attributes for the `:unit-statistics` entity — a per-patch
  snapshot of a unit's combat statistics.

  The statline is decomposed into one typed attribute per stat. `fields`
  is the single source of truth mapping each engine document key to its
  attribute and kind; the schema, the seed dumper, and the read-time
  reconstruction (`query.datalog.game`) all derive from it.

  Kinds:
  - `:long`       a scalar integer stat (e.g. `:unit-statistics/armor`)
  - `:boolean`    a flag (`:unit-statistics/is-large`)
  - `:strings`    a cardinality-many string list (e.g. abilities, attributes)
  - `:spell-keys` the draftable-spell keys, stored cardinality-many; the
                  engine document carries them as `[{\"key\" k} …]`

  `:unit-statistics/cost` is one of these attributes and stays queryable as
  a `:db.type/long` for draft/standings filters and aggregates.")

(def fields
  "[engine-doc-key attribute kind] for every stored stat field."
  [["ammunition"           :unit-statistics/ammunition           :long]
   ["armor"                :unit-statistics/armor                :long]
   ["barrier"              :unit-statistics/barrier              :long]
   ["bonus_vs_infantry"    :unit-statistics/bonus-vs-infantry    :long]
   ["bonus_vs_large"       :unit-statistics/bonus-vs-large       :long]
   ["charge_bonus"         :unit-statistics/charge-bonus         :long]
   ["cost"                 :unit-statistics/cost                 :long]
   ["health"               :unit-statistics/health               :long]
   ["leadership"           :unit-statistics/leadership           :long]
   ["melee_attack"         :unit-statistics/melee-attack         :long]
   ["melee_defence"        :unit-statistics/melee-defence        :long]
   ["missile_ap_damage"    :unit-statistics/missile-ap-damage    :long]
   ["missile_base_damage"  :unit-statistics/missile-base-damage  :long]
   ["missile_damage"       :unit-statistics/missile-damage       :long]
   ["range"                :unit-statistics/range                :long]
   ["speed"                :unit-statistics/speed                :long]
   ["unit_size"            :unit-statistics/unit-size            :long]
   ["weapon_ap_damage"     :unit-statistics/weapon-ap-damage     :long]
   ["weapon_damage"        :unit-statistics/weapon-damage        :long]
   ["weapon_strength"      :unit-statistics/weapon-strength      :long]
   ["is_large"             :unit-statistics/is-large             :boolean]
   ["abilities"            :unit-statistics/abilities            :strings]
   ["attributes"           :unit-statistics/attributes           :strings]
   ["melee_attack_types"   :unit-statistics/melee-attack-types   :strings]
   ["melee_modifiers"      :unit-statistics/melee-modifiers      :strings]
   ["missile_modifiers"    :unit-statistics/missile-modifiers    :strings]
   ["missile_damage_types" :unit-statistics/missile-damage-types :strings]
   ["draftable-spells"     :unit-statistics/draftable-spells     :spell-keys]])

(defn- field-attr-schema
  [kind]
  (case kind
    :long    {:db/valueType :db.type/long}
    :boolean {:db/valueType :db.type/boolean}
    (:strings :spell-keys) {:db/valueType   :db.type/string
                            :db/cardinality :db.cardinality/many}))

(def schema
  (into {:unit-statistics/eid   {:db/valueType :db.type/uuid
                                 :db/unique    :db.unique/identity}
         ;; Owning ref lives on the many side. Reverse-ref pull
         ;; (`{:unit-statistics/_unit [...]}`) gets every snapshot for a
         ;; unit; `[?s :unit-statistics/unit ?u]` is the query form.
         :unit-statistics/unit  {:db/valueType :db.type/ref}
         :unit-statistics/patch {:db/valueType :db.type/ref}}
        (map (fn [[_ attr kind]] [attr (field-attr-schema kind)]))
        fields))
