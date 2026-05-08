(ns com.devereux-henley.rts-domain.handlers.draft
  (:require
   [clojure.string :as str]
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.rules.draft :as rules.draft]
   [jsonista.core :as jsonista]
   [malli.core :as m]
   [malli.transform :as mt])
  (:import
   [java.time Instant]))

;; ─── Unit statistics parsing ──────────────────────────────────────────────────

(def ^:private stat-exclude-keys #{"abilities" "draftable-spells" "draftable-abilities" "mounts" "equipment"
                                   "cost" "health" "barrier" "is_large"
                                   "weapon_damage" "weapon_ap_damage"
                                   "missile_base_damage" "missile_ap_damage"
                                   "melee_attack_types" "missile_damage_types"
                                   "melee_modifiers" "missile_modifiers"
                                   "bonus_vs_infantry" "bonus_vs_large"
                                   "attributes"})

(defn- stat-entry
  "Converts a single decoded JSON entry into a stat map, or nil if it should be excluded.
   Drops structured fields (abilities, mounts, etc.), zero values, empty vectors,
   fields rendered separately (cost, health, barrier), and damage sub-components
   that are folded into tooltips on their parent stat. `:icon` is a kebab-case
   slug (e.g. \"melee-attack\") used by templates to build /icon/stat/<icon>.png."
  [[k v]]
  (when-not (stat-exclude-keys k)
    (let [label (str/replace k "_" " ")
          icon  (str/replace k "_" "-")]
      (cond
        (and (vector? v) (empty? v)) nil
        (= v 0)                      nil
        (vector? v)                  {:stat label :icon icon :value (str/join ", " v)}
        :else                        {:stat label :icon icon :value v}))))

(defn- damage-tooltip
  "Build a tooltip string for weapon-strength / missile-damage rows. Folds in
  base/AP split and any bonus-vs-infantry / bonus-vs-large values."
  [base ap bonus-inf bonus-lg base-label]
  (let [parts (cond-> []
                (and base ap) (conj (str base " " base-label " · " ap " AP"))
                (and bonus-inf (pos? bonus-inf)) (conj (str "+" bonus-inf " vs infantry"))
                (and bonus-lg (pos? bonus-lg)) (conj (str "+" bonus-lg " vs large")))]
    (when (seq parts) (str/join " · " parts))))

(defn- attach-stat-tooltips
  "Attaches a :tooltip string to weapon-strength and missile-damage entries,
   folding in base/AP breakdown and bonus-vs values. Also attaches :damage-types
   and :attack-modifiers badges to the appropriate rows:
     - melee attack    — melee damage types + melee contact modifiers
     - weapon strength — bonus-vs-infantry / bonus-vs-large badges
     - ammunition      — missile damage types + missile contact modifiers
                         (co-located with the ammo icon because they describe
                          the projectile, not the raw damage number)."
  [stats decoded]
  (let [w-dmg      (get decoded "weapon_damage")
        w-ap       (get decoded "weapon_ap_damage")
        m-base     (get decoded "missile_base_damage")
        m-ap       (get decoded "missile_ap_damage")
        bonus-inf  (get decoded "bonus_vs_infantry")
        bonus-lg   (get decoded "bonus_vs_large")
        melee-type (or (get decoded "melee_attack_types") [])
        miss-type  (or (get decoded "missile_damage_types") [])
        melee-mods (or (get decoded "melee_modifiers") [])
        miss-mods  (or (get decoded "missile_modifiers") [])
        bonus-mods (cond-> []
                     (and bonus-inf (pos? bonus-inf)) (conj "bonus-vs-infantry")
                     (and bonus-lg (pos? bonus-lg))   (conj "bonus-vs-large"))
        w-tip      (damage-tooltip w-dmg w-ap bonus-inf bonus-lg "dmg")
        m-tip      (damage-tooltip m-base m-ap bonus-inf bonus-lg "base")]
    (mapv (fn [{:keys [stat] :as s}]
            (cond-> s
              (and (= stat "weapon strength") w-tip)
              (assoc :tooltip w-tip)
              (and (= stat "missile damage") m-tip)
              (assoc :tooltip m-tip)
              (and (= stat "melee attack") (seq melee-type))
              (assoc :damage-types (vec melee-type))
              (and (= stat "melee attack") (seq melee-mods))
              (assoc :attack-modifiers (vec melee-mods))
              (and (= stat "weapon strength") (seq bonus-mods))
              (assoc :attack-modifiers bonus-mods)
              (and (= stat "ammunition") (seq miss-type))
              (assoc :damage-types (vec miss-type))
              (and (= stat "ammunition") (seq miss-mods))
              (assoc :attack-modifiers (vec miss-mods))))
          stats)))

(defn parse-unit-statistics
  "Parses a unit-statistics JSON string into a structured map with keys:
     :stats            — vector of {:stat name :value val :tooltip str-or-nil} for numeric/list fields
     :health           — integer HP value (rendered separately as a health bar)
     :barrier          — integer barrier value, or nil if absent/zero
     :abilities        — vector of ability key strings
     :draftable-spells — vector of spell key strings
     :equipment        — vector of raw equipment maps

  Mounts are no longer embedded in unit_statistics — they live in the
  `mount` / `unit_mount` tables and are fetched via
  `db/get-mounts-for-unit`."
  [unit-statistics-str]
  (let [decoded (m/decode db/unit-statistics-raw-schema
                          (jsonista/read-value unit-statistics-str (jsonista/object-mapper {:decode-key-fn name}))
                          db/unit-statistics-transformer)]
    {:stats
     (-> (into [] (keep stat-entry) decoded)
         (attach-stat-tooltips decoded))
     :health           (get decoded "health")
     :barrier          (let [b (get decoded "barrier")] (when (and b (pos? b)) b))
     :abilities        (get decoded "abilities")
     :draftable-spells (mapv #(get % "key") (get decoded "draftable-spells"))
     :equipment        (get decoded "equipment")
     :attributes       (mapv (fn [k]
                               {:key   k
                                :icon  (str/replace k "_" "-")
                                :label (->> (str/split k #"_")
                                            (map str/capitalize)
                                            (str/join " "))})
                             (or (get decoded "attributes") []))}))

;; ─── Stat percentages ─────────────────────────────────────────────────────────

(def ^:private stat-max-values
  {"armor"           100.0
   "armour"          100.0
   "leadership"      100.0
   "speed"           140.0
   "melee attack"    60.0
   "melee defence"   60.0
   "weapon strength" 700.0
   "charge bonus"    60.0
   "missile damage"  300.0
   "health"          12000.0
   "barrier"         1000.0})

(defn- add-stat-percentage
  "Attaches a :percentage key to a stat map, clamped to [0, 100], relative to known max values."
  [{:keys [stat value] :as s}]
  (let [max-val (get stat-max-values (str/lower-case (str stat)) 100.0)
        raw-val (cond
                  (number? value) (double value)
                  (string? value) (try (Double/parseDouble value) (catch Exception _ 0.0))
                  :else 0.0)]
    (assoc s :percentage (int (min 100 (Math/round (* 100.0 (/ raw-val max-val))))))))

;; ─── Unit hydration ──────────────────────────────────────────────────────────

(def ^:private unit-hydration-transformer
  (mt/transformer {:name :unit-hydrate}))

(def ^:private hydrated-unit-schema
  (m/schema
   [:map {:decode/unit-hydrate
          (fn [unit]
            (let [{:keys [stats abilities]} (parse-unit-statistics (:unit-statistics unit))]
              (assoc unit
                     :parsed-stats     stats
                     :parsed-abilities abilities
                     :is-lord          (= "Lord" (:unit-category-name unit))
                     :is-unique        (= 1 (:is-unique unit)))))}]))

(defn hydrate-units-with-stats
  "Enriches each unit entity with :parsed-stats, :parsed-abilities, :is-lord, and :is-unique
   derived from its :unit-statistics JSON string."
  [units]
  (mapv #(m/decode hydrated-unit-schema % unit-hydration-transformer) units))

;; ─── Family grouping ─────────────────────────────────────────────────────────

(defn- canonical-variant
  "Picks the row that represents a family in the roster.  Prefers — in
  order — the row whose `:name` already matches the family label
  (`:family-name`, i.e. unmarked + un-lore-suffixed), then the
  unmarked-no-lore row, then the unmarked row, then the first in id
  order.  The canonical variant's portrait + cost label drives the
  roster card; per-(mark, lore) eid/cost lives in `:family-variants`."
  [members]
  (or (first (filter #(and (:family-name %) (= (:name %) (:family-name %))) members))
      (first (filter #(and (nil? (:mark %)) (nil? (:lore %))) members))
      (first (filter (comp nil? :mark) members))
      (first members)))

(defn group-units-by-family
  "Collapses a unit collection so each (family-name, faction-eid) family has
  a single representative row.  The representative carries the full
  hydrated-unit shape plus a `:family-variants` vector — one entry
  per (mark, lore) variant in id-stable order — that the unit panel
  uses to drive the Mark and Lore selectors.  Single-variant families
  pass through unchanged with a single-entry `:family-variants` so
  the template can apply the same logic uniformly.

  Grouping uses `:family-name` (the seed-stamped family identifier
  shared across mark + lore variants) rather than `:name` (which is
  the engine-original string and differs across variants)."
  [units]
  (->> units
       (group-by (juxt #(or (:family-name %) (:name %)) :faction-eid))
       (sort-by (comp :id first val))
       (mapv (fn [[_family members]]
               (let [ordered   (sort-by :id members)
                     canonical (canonical-variant ordered)
                     variants  (mapv (fn [u]
                                       {:eid  (:eid u)
                                        :mark (:mark u)
                                        :lore (:lore u)
                                        :name (:name u)
                                        :cost (:cost u)})
                                     ordered)]
                 (assoc canonical :family-variants variants))))))

;; ─── Section context ──────────────────────────────────────────────────────────

(defn- section-percentage
  "Returns cost as an integer percentage of max-val, clamped to [0, 100]. Returns 0 if max-val is nil or zero."
  [cost max-val]
  (if (and max-val (pos? max-val))
    (int (min 100 (Math/round (double (* 100 (/ cost max-val))))))
    0))

(defn build-section-context
  "Builds the template context map for a draft section (\"main\" or \"reinforcements\"),
   including cost totals, budget percentage, lord/non-lord split, and over-budget flag."
  [section units draft-eid game-mode]
  (let [is-main       (= section "main")
        section-label (if is-main "Main Army" "Reinforcements")
        section-id    (if is-main "main-army-section" "reinforcements-section")
        section-max   (if is-main (:draft-value game-mode) (:reinforcement-value game-mode))
        cost          (reduce (fn [s u] (+ s (or (:total-cost u) (:cost u) 0))) 0 units)
        percentage    (section-percentage cost section-max)
        lord-unit     (first (filter :is-lord units))
        non-lords     (vec (remove :is-lord units))]
    {:section             section
     :section-label       section-label
     :section-id          section-id
     :is-main             is-main
     :section-units       units
     :lord-unit           lord-unit
     :non-lord-units      non-lords
     :section-cost        cost
     :section-max         section-max
     :section-percentage  percentage
     :section-near-limit  (> percentage 85)
     :section-over-budget (and (some? section-max) (> cost section-max))
     :draft-eid           draft-eid}))

;; ─── Draft state ──────────────────────────────────────────────────────────────

(defn- draft-default-name
  "Fallback display name when a draft hasn't been renamed: 'Faction draft
  mm/dd/yyyy'. Both inputs come from the SQL projection (faction-name +
  created-at-display)."
  [{:keys [faction-name created-at-display]}]
  (when (and faction-name created-at-display)
    (str faction-name " draft " created-at-display)))

(defn- with-display-name
  "Attaches :display-name — the custom :name (trimmed, ignored when
  blank) when set, else the faction/date default. Templates read
  :display-name so they never need to know which side of the OR they
  got."
  [draft]
  (let [trimmed (some-> (:name draft) str/trim not-empty)]
    (assoc draft :display-name (or trimmed (draft-default-name draft)))))

(defn get-draft-by-eid
  "Fetches a draft by eid and attaches :type :game/draft + :display-name."
  [dependencies eid]
  (-> (db/get-draft-by-eid (:connection dependencies) eid)
      with-display-name
      (assoc :type :game/draft)))

(defn create-draft
  "Creates a new draft, stamping :created-at and :updated-at, and attaches :type :game/draft."
  [dependencies create-specification]
  (let [created-at (Instant/now)
        updated-at created-at]
    (-> (db/create-draft (:connection dependencies)
                         (-> create-specification
                             (assoc :created-at created-at)
                             (assoc :updated-at updated-at)))
        with-display-name
        (assoc :type :game/draft))))

(defn update-draft
  "Applies a partial update to a draft. Currently only :name is
  mutable — empty/whitespace-only clears the stored name so the default
  (faction + date) renders again. Returns the refreshed draft with
  :display-name recomputed."
  [dependencies eid {:keys [name] :as _updates}]
  (let [normalised (when name (not-empty (str/trim name)))]
    (-> (db/update-draft (:connection dependencies) eid {:name normalised})
        with-display-name
        (assoc :type :game/draft))))

(defn get-drafts-for-player
  "Returns all drafts for a player, each tagged with :type :game/draft."
  [dependencies player-sub]
  (mapv (fn [draft] (-> draft with-display-name (assoc :type :game/draft)))
        (db/get-drafts-for-player (:connection dependencies) player-sub)))

(defn get-drafts-for-player-by-game
  "Returns all drafts for a player scoped to a specific game, each tagged with :type :game/draft."
  [dependencies player-sub game-eid]
  (mapv (fn [draft] (-> draft with-display-name (assoc :type :game/draft)))
        (db/get-drafts-for-player-by-game (:connection dependencies) player-sub game-eid)))

(def ^:private state-entry-schema
  (m/schema
   [:map
    [:entry-eid  {:optional true} [:maybe :uuid]]
    [:unit-eid :uuid]
    [:mount      {:optional true} [:maybe :string]]
    [:lore       {:optional true} [:maybe :string]]
    [:level      {:optional true, :default 0} [:int {:min 0 :max 9}]]
    [:abilities  {:optional true, :default []} [:sequential :string]]
    [:spells     {:optional true, :default []} [:sequential :string]]
    [:items      {:optional true, :default []} [:sequential :string]]
    [:total-cost {:optional true} [:maybe :int]]]))

(def ^:private state-entry-transformer
  (mt/transformer mt/string-transformer
                  (mt/default-value-transformer {::mt/add-optional-keys true})))

(defn- parse-state-entry
  "Decodes a raw JSON-decoded state entry into a typed map via Malli.
   Converts :unit-eid / :entry-eid strings to UUIDs and defaults
   :abilities/:spells/:items to []. Backfills :entry-eid with a fresh UUID
   when absent so legacy state rows self-heal on first read."
  [entry]
  (let [decoded (m/decode state-entry-schema entry state-entry-transformer)]
    (cond-> decoded
      (nil? (:entry-eid decoded)) (assoc :entry-eid (random-uuid)))))

(defn get-draft-state
  "Returns {:main [entry …] :reinforcements [entry …]} where each entry is
   {:unit-eid uuid :mount str-or-nil :spells [str] :items [str] :total-cost int-or-nil}.
   Returns empty collections when no state exists."
  [dependencies draft-eid]
  (if-let [row (db/get-draft-state-by-draft (:connection dependencies) draft-eid)]
    (let [parsed (jsonista/read-value (:state row) (jsonista/object-mapper {:decode-key-fn keyword}))]
      {:main           (mapv parse-state-entry (get parsed :main []))
       :reinforcements (mapv parse-state-entry (get parsed :reinforcements []))})
    {:main [] :reinforcements []}))

(defn- serialise-entry
  "Encodes a typed state entry to a JSON-serialisable map via Malli.
   Converts :unit-eid UUID to string."
  [e]
  (m/encode state-entry-schema e state-entry-transformer))

(defn set-draft-state
  "Persists {:main [entry …] :reinforcements [entry …]} as an atomic JSON blob.
   Each entry: {:unit-eid uuid :mount str-or-nil :abilities [str] :spells [str] :items [str] :total-cost int-or-nil}."
  [dependencies draft-eid state]
  (db/upsert-draft-state (:connection dependencies) draft-eid
                         (jsonista/write-value-as-string
                          {:main           (mapv serialise-entry (:main state))
                           :reinforcements (mapv serialise-entry (:reinforcements state))})))

(defn get-spells-by-keys
  "Returns a map of spell-key → spell entity for the given spell keys."
  [dependencies spell-keys]
  (db/get-spells-by-keys (:connection dependencies) spell-keys))

(defn get-abilities-by-keys
  "Returns a map of ability-key → ability entity for the given ability keys."
  [dependencies ability-keys]
  (db/get-abilities-by-keys (:connection dependencies) ability-keys))

(defn get-items-for-unit
  "Returns all active items linked to the given unit EID."
  [dependencies unit-eid]
  (db/get-items-for-unit (:connection dependencies) unit-eid))

(defn get-mounts-for-unit
  "Returns all active mounts linked to the given unit EID."
  [dependencies unit-eid]
  (db/get-mounts-for-unit (:connection dependencies) unit-eid))

(defn get-spells-for-lore
  "Returns the canonical spell list for the given lore key."
  [dependencies lore-key]
  (db/get-spells-for-lore (:connection dependencies) lore-key))

;; ─── Mount overrides ──────────────────────────────────────────────────────────

(defn- parse-granted-ability-keys
  "Parses the raw granted_ability_keys TEXT column (a JSON array or null)
  into a vector of key strings. Returns nil when the column is empty;
  invalid JSON propagates as an exception since that would mean the seed
  pipeline wrote garbage."
  [raw]
  (when (and raw (seq raw))
    (vec (jsonista/read-value raw))))

(defn- hydrate-granted-abilities
  "Resolves a seq of ability keys against the ability table, dropping any
  keys that don't exist. Returned maps match draft-ability shape."
  [conn ability-keys]
  (when (seq ability-keys)
    (let [by-key (db/get-abilities-by-keys conn ability-keys)]
      (into []
            (keep (fn [k]
                    (when-let [{:keys [name eid description cost]} (get by-key k)]
                      {:key         k           :name name        :eid eid
                       :description description :cost (or cost 0)})))
            ability-keys))))

(defn hydrate-mount-overrides
  "Parses a mount row's stats_override + granted_ability_keys TEXT columns
  and attaches:
    :stats-override       — [draft-unit-stat] with percentages
    :health-override      — int or nil
    :barrier-override     — int or nil
    :attributes-override  — vec of {:key :icon :label} or nil
    :granted-abilities    — vec of draft-ability (resolved from key list)
  Mounts without override data pass through untouched except for an empty
  :granted-abilities."
  [conn mount]
  (let [raw-stats    (:stats-override mount)
        raw-granted  (:granted-ability-keys mount)
        parsed       (when (and raw-stats (seq raw-stats))
                       (parse-unit-statistics raw-stats))
        granted-keys (parse-granted-ability-keys raw-granted)
        granted      (hydrate-granted-abilities conn granted-keys)]
    (cond-> (assoc mount :granted-abilities (or granted []))
      parsed (assoc :stats-override      (mapv add-stat-percentage (:stats parsed))
                    :health-override     (:health parsed)
                    :barrier-override    (:barrier parsed)
                    :attributes-override (vec (:attributes parsed))))))

;; ─── Lore overrides ──────────────────────────────────────────────────────────

(defn- hydrate-spell-keys
  "Resolves a seq of spell keys against the spell table into the
  {:key :eid :name :mana-cost :cost} shape the draft-unit resource uses.
  Skips unknown keys; preserves the order of `spell-keys`."
  [conn spell-keys]
  (when (seq spell-keys)
    (let [by-key (db/get-spells-by-keys conn spell-keys)]
      (into []
            (keep (fn [k]
                    (when-let [{:keys [eid name mana-cost cost]} (get by-key k)]
                      {:key       k
                       :eid       eid
                       :name      name
                       :mana-cost (or mana-cost 0)
                       :cost      (or cost 0)})))
            spell-keys))))

(defn- lore-spells
  "Canonical draftable-spell list for a lore, as {:key :eid :name :mana-cost :cost}
  records. The pool is invariant across all units that can access the lore,
  so we fetch it from spell_lore (the source of truth) rather than storing
  a redundant copy per (unit, lore)."
  [conn lore-key]
  (mapv (fn [{:keys [key eid name mana-cost cost]}]
          {:key       key
           :eid       eid
           :name      name
           :mana-cost (or mana-cost 0)
           :cost      (or cost 0)})
        (db/get-spells-for-lore conn lore-key)))

;; ─── Cost calculation ─────────────────────────────────────────────────────────

(defn apply-level-cost
  "Applies the engine veterancy formula
     adjusted = round(base * cost_multiplier) + fixed_cost
   using the provided unit_level_cost row. Falls back to `base` when the
   row is absent (e.g. data-source down) — no level adjustment in that case
   is safer than refusing to price the unit."
  [base level-cost-row]
  (if (and level-cost-row (some? base))
    (long (+ (Math/round (* (double base) (double (:cost-multiplier level-cost-row))))
             (or (:fixed-cost level-cost-row) 0)))
    (or base 0)))

(defn- compute-unit-total-cost
  "Returns level-adjusted-base-cost + mount-cost + ability-cost + spell-cost + item-cost.

   The level adjustment uses the unit_level_cost row matching `:level` (0-9,
   defaulting to 0). Mount/ability/spell/item costs are NOT scaled by level —
   the engine only veterans the base unit price.

   selections: {:mount str-or-nil :level int-or-nil :abilities [ability-key]
                :spells [spell-key] :items [item-key]}
   :mount is the mount `type` key (e.g. \"wh2_dlc09_anc_mount_skeleton_chariot\"),
   not a display name. Cost comes from unit_mount.cost via get-mounts-for-unit."
  [unit-hydrated selections conn]
  (let [base-cost     (or (:cost unit-hydrated) 0)
        level         (or (:level selections) 0)
        level-costs   (db/get-unit-level-costs conn)
        adjusted-base (apply-level-cost base-cost (get level-costs level))
        mount-key     (:mount selections)
        mount-cost    (when mount-key
                        (:cost (first (filter #(= mount-key (:key %))
                                              (db/get-mounts-for-unit conn (:eid unit-hydrated))))))
        ability-keys  (not-empty (:abilities selections []))
        ability-cost  (when ability-keys
                        (->> (db/get-abilities-by-keys conn ability-keys)
                             vals
                             (map #(or (:cost %) 0))
                             (reduce + 0)))
        spell-keys    (not-empty (:spells selections []))
        spell-cost    (when spell-keys
                        (->> (db/get-spells-by-keys conn spell-keys)
                             vals
                             (map #(or (:cost %) 0))
                             (reduce + 0)))
        item-keys     (not-empty (set (:items selections [])))
        item-cost     (when item-keys
                        (->> (db/get-items-for-unit conn (:eid unit-hydrated))
                             (filter #(item-keys (:key %)))
                             (map #(or (:cost %) 0))
                             (reduce + 0)))]
    (+ adjusted-base (or mount-cost 0) (or ability-cost 0) (or spell-cost 0) (or item-cost 0))))

;; ─── Shared helpers ───────────────────────────────────────────────────────────

(defn- faction-unit-index
  "Returns a map of unit-eid → hydrated-unit for all units in the draft's faction."
  [conn faction-eid]
  (into {} (map (juxt :eid identity) (hydrate-units-with-stats (db/get-units-for-faction conn faction-eid)))))

(defn- hydrate-section
  "Resolves state entries to their hydrated units, attaching :total-cost,
   :level, and the entry's :entry-eid.  Each variant unit row carries
   its own portrait at /card/unit/<eid>.png — no lore-portrait override
   is needed since the entry's `:unit-eid` already points at the
   specific (mark, lore) variant the player picked."
  [_conn unit-by-eid entries]
  (mapv (fn [entry]
          (when-let [u (get unit-by-eid (:unit-eid entry))]
            (assoc u
                   :total-cost (or (:total-cost entry) (:cost u))
                   :level      (:level entry)
                   :entry-eid  (:entry-eid entry))))
        entries))

(defn- hydrated-section-context
  "Hydrates `state`'s entries for `section` and wraps them in a
   build-section-context result."
  [conn unit-by-eid state section draft-eid game-mode]
  (build-section-context section
                         (hydrate-section conn unit-by-eid (get state (keyword section) []))
                         draft-eid
                         game-mode))

(defn- section-budget
  "Projects a section context down to the budget fragment fields used by
   mutation responses."
  [section-ctx]
  (select-keys section-ctx
               [:section :section-label :section-id :section-cost
                :section-max :section-percentage :section-near-limit
                :section-over-budget]))

(defn- section-ref
  "Projects a section context down to the addressing fields a slot fragment
   needs to render edit/remove URLs."
  [section-ctx]
  (select-keys section-ctx [:section :section-id :section-label :draft-eid]))

(defn- slot-unit
  "Projects a hydrated unit to the draft-section-unit shape rendered by
   slot fragments."
  [unit entry-eid total-cost level]
  {:eid        (:eid unit)
   :entry-eid  entry-eid
   :name       (:name unit)
   :total-cost total-cost
   :level      level
   :is-lord    (boolean (:is-lord unit))})

;; ─── Family selectors ────────────────────────────────────────────────────────

(def ^:private lore-suffix-re #" \(([^()]+)\)$")

(def ^:private lore-name-prefix "Lore of ")

(defn- lore-label
  "Returns the lore display label for a family variant.  Prefers the
  joined `:lore-name` from the `lore` catalogue (e.g. \"Lore of Fire\"
  → \"Fire\", \"Lore of Yang\" → \"Yang\"), which works uniformly for
  every wizard family regardless of how the engine names its rows.
  Falls back to parsing a trailing `(<Suffix>)` from the variant's
  `:name` so families whose rows lack a `:lore` pin (currently the
  Vampire Fleet Admiral pistol/polearm splits) still render a label.
  Returns nil when neither source yields one."
  [{:keys [name lore-name]}]
  (or (when lore-name
        (cond-> lore-name
          (str/starts-with? lore-name lore-name-prefix)
          (subs (count lore-name-prefix))))
      (some-> name (->> (re-find lore-suffix-re)) second)))

(defn- family-marks
  "Reduces `family-variants` to one Mark-selector option per distinct
  `:mark` value, preferring the variant whose `:lore` matches
  `current-lore` so a mark swap preserves the lore choice when
  possible.  The current unit's eid is preserved as the representative
  for `current-mark` so the dropdown's selected option matches the
  panel's unit.  Returns the slim `family-mark-option` shape — only
  the fields the selector actually renders."
  [family-variants current-eid current-mark current-lore]
  (let [by-mark (group-by :mark family-variants)]
    (->> (keys by-mark)
         (sort-by #(or % ""))
         (mapv (fn [m]
                 (let [siblings (get by-mark m)
                       chosen   (or (when (= m current-mark)
                                      (first (filter #(= current-eid (:eid %)) siblings)))
                                    (first (filter #(= current-lore (:lore %)) siblings))
                                    (first siblings))]
                   (select-keys chosen [:eid :mark :cost])))))))

(defn- family-lores
  "Reduces `family-variants` to the Lore-selector options under
  `current-mark` (a single mark's slate of lore variants).  Returns
  the slim `family-lore-option` shape — eid, cost, and a derived
  `:lore-label` (see `lore-label`).  Empty when the family has no
  lore dimension under the current mark."
  [family-variants current-mark]
  (->> family-variants
       (filter #(= current-mark (:mark %)))
       (mapv (fn [v] {:eid        (:eid v)
                      :cost       (:cost v)
                      :lore-label (lore-label v)}))))

;; ─── Composite domain operations ──────────────────────────────────────────────

(declare apply-selections-to-unit build-draft-unit-resource)

(defn get-draft-unit-details
  "Returns a flat :draft/unit resource for a unit in the context of a draft:
   unit identity/stats/abilities merged with the per-draft option catalog
   (items, mounts, draftable spells, passive spells) and a flag for whether
   reinforcements are enabled for the enclosing game mode.

   Abilities and spells are split into two groups by cost:
     - passive (cost = 0): always on the character; shown in a readonly section
     - draftable (cost > 0): purchasable options shown in a selectable section

   When `selections` is a map ({:mount :items :spells :abilities}) the
   catalog is marked with :selected flags, the chosen mount's stats /
   health / barrier / attributes overlay the base unit, granted abilities
   surface as :mount-granted-abilities, and :total-cost reflects the live
   combination. In preview mode callers pass nil."
  ([dependencies draft-eid unit-eid]
   (get-draft-unit-details dependencies draft-eid unit-eid nil))
  ([dependencies draft-eid unit-eid selections]
   (let [unit (build-draft-unit-resource dependencies draft-eid unit-eid)]
     (if selections
       (apply-selections-to-unit (:connection dependencies) unit selections)
       unit))))

(defn- build-draft-unit-resource
  "The original unit-detail assembler, now private. Returns the draft-unit
  resource without any selection overlay applied.

  Abilities granted by any of the unit's mounts are excluded from the
  base passive/draftable lists — they only surface under
  :mount-granted-abilities when the corresponding mount is selected.
  This prevents the hand-curated seed's habit of folding mount-only
  abilities (e.g. Bloodroar on Karl Franz) into the foot unit's
  draftable pool.

  Spells: when the unit has a `:lore` set, the draftable + passive
  pool is fetched from spell_lore (the canonical per-lore catalogue).
  Otherwise the unit_statistics JSON's `draftable-spells` list is
  the source of truth — used for unique characters with bespoke
  spell pools (e.g. Malagor) and non-spellcasters."
  [dependencies draft-eid unit-eid]
  (let [conn                                                                 (:connection dependencies)
        draft                                                                (db/get-draft-by-eid conn draft-eid)
        game-mode                                                            (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        unit                                                                 (db/get-unit-by-eid conn unit-eid)
        {:keys [stats health barrier abilities draftable-spells attributes]} (parse-unit-statistics (:unit-statistics unit))
        unit-statistics                                                      (mapv add-stat-percentage stats)
        mounts                                                               (mapv #(hydrate-mount-overrides conn %)
                                                                                   (db/get-mounts-for-unit conn unit-eid))
        mount-only-keys                                                      (into #{}
                                                                                   (mapcat (fn [m] (map :key (:granted-abilities m))))
                                                                                   mounts)
        base-ability-keys                                                    (remove mount-only-keys abilities)
        ability-by-key                                                       (db/get-abilities-by-keys conn base-ability-keys)
        all-abilities                                                        (into []
                                                                                   (keep (fn [k]
                                                                                           (when-let [{:keys [name eid description cost]} (get ability-by-key k)]
                                                                                             {:key k :name name :eid eid :description description :cost (or cost 0)})))
                                                                                   base-ability-keys)
        passive-abilities                                                    (filterv #(= 0 (:cost %)) all-abilities)
        draftable-abilities                                                  (filterv #(pos? (:cost %)) all-abilities)
        all-spells                                                           (if-let [lore-key (:lore unit)]
                                                                               (lore-spells conn lore-key)
                                                                               (or (hydrate-spell-keys conn draftable-spells) []))
        passive-spells                                                       (filterv #(= 0 (:cost %)) all-spells)
        draftable-spells-v                                                   (filterv #(pos? (:cost %)) all-spells)
        items                                                                (db/get-items-for-unit conn unit-eid)
        family-variants                                                      (vec (db/get-family-variants-by-eid conn unit-eid))
        marks-row                                                            (family-marks family-variants (:eid unit) (:mark unit) (:lore unit))
        lores-row                                                            (family-lores family-variants (:mark unit))]
    (assoc unit
           :type                :draft/unit
           :draft-eid           draft-eid
           :unit-statistics     unit-statistics
           :health              health
           :barrier             barrier
           :attributes          (vec attributes)
           :parsed-abilities    all-abilities
           :passive-abilities   passive-abilities
           :draftable-abilities draftable-abilities
           :items               items
           :mounts              mounts
           :passive-spells      passive-spells
           :draftable-spells    draftable-spells-v
           :has-passives        (boolean (or (seq passive-abilities) (seq passive-spells)))
           :family-marks        marks-row
           :family-lores        lores-row
           :validation          {:can-add-to-reinforcements? (= 1 (:reinforcements-enabled game-mode))})))

(defn- mark-selected
  "Returns options with :selected true/false set from whether each :key is in selected-keys."
  [options selected-keys]
  (mapv (fn [opt] (assoc opt :selected (contains? selected-keys (:key opt)))) options))

(declare get-draft-entry)

(defn get-draft-entry-details
  "Returns a :draft/entry resource for a placed entry: addressing fields
   (eid, draft-eid, unit-eid, section) and the selection state as raw key
   lists (:mount, :abilities, :spells, :items). Clients that want the
   unit's catalog fetch it via `?embed=unit`, which runs
   embed-unit-for-entry.

   When `overrides` is a map it fully replaces the entry's persisted
   selection for rendering purposes only (it's not written back). This is
   how the GET endpoint renders a live preview of the panel as the user
   toggles mounts/items/etc. on the form."
  ([dependencies draft-eid entry-eid section]
   (get-draft-entry-details dependencies draft-eid entry-eid section nil))
  ([dependencies draft-eid entry-eid section overrides]
   (when-let [entry (get-draft-entry dependencies draft-eid entry-eid section)]
     (let [source (or overrides entry)]
       {:type      :draft/entry
        :eid       (:entry-eid entry)
        :draft-eid draft-eid
        :unit-eid  (:unit-eid entry)
        :section   section
        :mount     (:mount source)
        :level     (or (:level source) 0)
        :abilities (vec (:abilities source))
        :spells    (vec (:spells source))
        :items     (vec (:items source))}))))

(defn- apply-mount-overrides
  "If mount-key identifies one of the unit's mounts, overlay its stats /
  health / barrier / attributes and expose its granted abilities on the
  unit. Mounts without override data leave the base fields untouched.
  Returns the unit with :selected flags set on mounts and the overlay
  applied."
  [unit mount-key]
  (let [mounts   (or (:mounts unit) [])
        marked   (mapv (fn [m] (assoc m :selected (= mount-key (:key m)))) mounts)
        selected (when mount-key (first (filter #(= mount-key (:key %)) mounts)))
        unit'    (assoc unit :mounts marked)]
    (if-not selected
      (assoc unit' :mount-granted-abilities [])
      (cond-> (assoc unit' :mount-granted-abilities (or (:granted-abilities selected) []))
        (:stats-override selected)      (assoc :unit-statistics (:stats-override selected))
        (:health-override selected)     (assoc :health (:health-override selected))
        (:barrier-override selected)    (assoc :barrier (:barrier-override selected))
        (:attributes-override selected) (assoc :attributes (:attributes-override selected))))))

(defn apply-selections-to-unit
  "Given a hydrated draft-unit resource and a selection map
  ({:mount :items :spells :abilities}), marks :selected on draftable
  options, overlays the chosen mount's stats/health/barrier/attributes,
  exposes granted abilities as :mount-granted-abilities, surfaces the raw
  :mount key so the template can pre-check its radio, and assocs
  :total-cost reflecting base + mount + item + spell + ability costs.

  Lore is no longer a selection: each unit row already represents one
  (mark, lore) variant, so its `:draftable-spells` are already the
  correct pool by the time we get here.  The user toggles lore by
  swapping `unit-eid` (handled in `update-unit-in-draft`)."
  [conn unit {:keys [mount items spells abilities level] :as selections}]
  (let [ability-set (set abilities)
        spell-set   (set spells)
        item-set    (set items)
        overlaid    (as-> unit $
                      (assoc $ :mount mount :level (or level 0))
                      (apply-mount-overrides $ mount)
                      (update $ :draftable-abilities mark-selected ability-set)
                      (update $ :draftable-spells mark-selected spell-set)
                      (update $ :items mark-selected item-set))
        total       (compute-unit-total-cost overlaid selections conn)]
    (assoc overlaid :total-cost total)))

(defn embed-unit-for-entry
  "Embed function for the `unit` embed on a draft-entry-resource. Loads the
   draft-scoped unit resource for the entry's unit-eid and marks :selected
   on draftable abilities, draftable spells, items, and mounts based on the
   entry's stored selection key lists. When a mount is selected, overlays
   the mount's stats/health/barrier/attributes and exposes its granted
   abilities as :mount-granted-abilities. Adds :total-cost reflecting the
   live selection (base + mount + items + spells + abilities). Assocs the
   result under [:_embedded :unit]."
  [dependencies entry-resource]
  (let [selections {:mount     (:mount entry-resource)
                    :lore      (:lore entry-resource)
                    :level     (:level entry-resource)
                    :abilities (:abilities entry-resource)
                    :spells    (:spells entry-resource)
                    :items     (:items entry-resource)}
        unit       (build-draft-unit-resource dependencies
                                              (:draft-eid entry-resource)
                                              (:unit-eid entry-resource))]
    (assoc-in entry-resource [:_embedded :unit]
              (apply-selections-to-unit (:connection dependencies) unit selections))))

(defn add-unit-to-draft
  "Validates and adds a unit to the specified section of a draft.
   selections: {:mount str-or-nil :abilities [ability-key] :spells [spell-key] :items [item-key]}
   Returns {:type :draft/add-success ...} on success, {:type :draft/add-error ...} on violation."
  [dependencies draft-eid unit-eid section selections]
  (let [;; Form-encoded submissions send an empty-string `mount` when the
        ;; "No mount" radio is selected; normalise to nil so downstream
        ;; lookups and persistence see the absent-mount shape.
        selections  (update selections :mount not-empty)
        conn        (:connection dependencies)
        draft       (db/get-draft-by-eid conn draft-eid)
        game-mode   (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        state       (get-draft-state dependencies draft-eid)
        section-k   (keyword section)
        unit-by-eid (faction-unit-index conn (:faction-eid draft))
        unit        (get unit-by-eid unit-eid)
        section-max (if (= section "main")
                      (:draft-value game-mode)
                      (:reinforcement-value game-mode))]
    (if (nil? unit)
      {:type :draft/add-error :message "Unit not found in this faction's roster."}
      (let [total-cost   (compute-unit-total-cost unit selections conn)
            army-entries (concat
                          (keep (fn [e] (when-let [u (get unit-by-eid (:unit-eid e))] (assoc u :section "main")))
                                (:main state))
                          (keep (fn [e] (when-let [u (get unit-by-eid (:unit-eid e))] (assoc u :section "reinforcements")))
                                (:reinforcements state)))
            section-cost (reduce (fn [s entry]
                                   (if-let [u (get unit-by-eid (:unit-eid entry))]
                                     (+ s (or (:total-cost entry) (:cost u) 0))
                                     s))
                                 0
                                 (get state section-k []))
            violation    (rules.draft/validate-add
                          army-entries unit section
                          section-cost section-max total-cost)]
        (if violation
          violation
          (let [new-entry-eid (random-uuid)
                new-state     (update state section-k (fnil conj [])
                                      {:entry-eid  new-entry-eid
                                       :unit-eid   unit-eid
                                       :mount      (:mount selections)
                                       :level      (or (:level selections) 0)
                                       :abilities  (or (:abilities selections) [])
                                       :spells     (or (:spells selections) [])
                                       :items      (or (:items selections) [])
                                       :total-cost total-cost})]
            (set-draft-state dependencies draft-eid new-state)
            (let [section-ctx (hydrated-section-context conn unit-by-eid new-state section draft-eid game-mode)]
              {:type     :draft/add-success
               :section  (section-ref section-ctx)
               :new-unit (slot-unit unit new-entry-eid total-cost
                                    (or (:level selections) 0))
               :budget   (section-budget section-ctx)})))))))

(defn- find-entry-index
  "Returns the index of the first entry with the given :entry-eid in entries,
   or nil when no entry matches."
  [entries entry-eid]
  (first (keep-indexed (fn [i entry] (when (= entry-eid (:entry-eid entry)) i))
                       entries)))

(defn remove-unit-from-draft
  "Removes the entry matching entry-eid from the given section of a draft's state
   and returns a :draft/remove-success response map."
  [dependencies draft-eid entry-eid section]
  (let [conn          (:connection dependencies)
        draft         (db/get-draft-by-eid conn draft-eid)
        game-mode     (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        state         (get-draft-state dependencies draft-eid)
        section-k     (keyword section)
        old-list      (get state section-k [])
        index         (find-entry-index old-list entry-eid)
        removed-entry (when index (nth old-list index))
        unit-by-eid   (faction-unit-index conn (:faction-eid draft))
        removed-unit  (when removed-entry (get unit-by-eid (:unit-eid removed-entry)))
        new-state     (assoc state section-k
                             (if (some? index)
                               (into [] (concat (subvec old-list 0 index) (subvec old-list (inc index))))
                               old-list))]
    (set-draft-state dependencies draft-eid new-state)
    (let [section-ctx (hydrated-section-context conn unit-by-eid new-state section draft-eid game-mode)]
      {:type              :draft/remove-success
       :removed-entry-eid entry-eid
       :removed-is-lord   (boolean (:is-lord removed-unit))
       :budget            (section-budget section-ctx)})))

(defn get-draft-entry
  "Returns the state entry matching entry-eid in the given section, or nil."
  [dependencies draft-eid entry-eid section]
  (let [state   (get-draft-state dependencies draft-eid)
        entries (get state (keyword section) [])]
    (some #(when (= entry-eid (:entry-eid %)) %) entries)))

(defn- valid-family-swap?
  "Sanity check for slot mark / lore switching: the new variant must
  share the entry's family (same family-name + faction).  Family-name
  is the shared identifier across (mark, lore) variants; engine `name`
  differs across them ('Daemon Prince of Khorne' vs base 'Daemon
  Prince', 'Archmage (High)' vs 'Archmage (Light)') so comparing on
  it would reject every cross-variant swap.  Falls back to `:name` for
  unit rows that predate the family-name backfill."
  [unit-by-eid old-eid new-eid]
  (let [old-unit (get unit-by-eid old-eid)
        new-unit (get unit-by-eid new-eid)
        old-key  (or (:family-name old-unit) (:name old-unit))
        new-key  (or (:family-name new-unit) (:name new-unit))]
    (and old-unit new-unit
         (= old-key new-key))))

(defn update-unit-in-draft
  "Validates and replaces the selections of an already-placed draft entry.
   entry-eid identifies the specific state entry; section says which list it
   lives in. selections has the same shape as add-unit-to-draft selections.

   When `selections` carries `:unit-eid`, the entry's unit row is
   swapped to a different family variant (different mark and/or
   different lore — both are unit-eid-keyed in the family-variants
   catalogue).  The mount/abilities/spells/items selections are
   cleared because they're keyed to the old row's catalog.  The new
   variant must share the entry's family (same family-name + faction)
   to prevent a swap into an unrelated unit.

   Returns {:type :draft/update-success …} on success or
   {:type :draft/update-error …} on violation or missing entry."
  [dependencies draft-eid entry-eid section selections]
  (let [selections-0  (update selections :mount not-empty)
        conn          (:connection dependencies)
        draft         (db/get-draft-by-eid conn draft-eid)
        game-mode     (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        state         (get-draft-state dependencies draft-eid)
        section-k     (keyword section)
        section-list  (get state section-k [])
        index         (find-entry-index section-list entry-eid)
        existing      (when index (nth section-list index))
        unit-by-eid   (faction-unit-index conn (:faction-eid draft))
        section-max   (if (= section "main")
                        (:draft-value game-mode)
                        (:reinforcement-value game-mode))
        new-unit-eid  (:unit-eid selections-0)
        family-swap?  (and existing
                           new-unit-eid
                           (not= new-unit-eid (:unit-eid existing)))
        ;; Family swap clears the catalog-keyed selections — they're
        ;; per-row, not portable across variants (a different lore
        ;; row's draftable-spells differ from the old, and ability/
        ;; mount/item catalogs are mark-specific).
        selections    (cond-> (dissoc selections-0 :unit-eid)
                        family-swap? (-> (assoc :mount nil)
                                         (assoc :abilities [])
                                         (assoc :spells [])
                                         (assoc :items [])))
        effective-eid (if family-swap? new-unit-eid (some-> existing :unit-eid))]
    (cond
      (nil? existing)
      {:type :draft/update-error :message "Unit not found in this section."}

      (and family-swap? (not (valid-family-swap? unit-by-eid (:unit-eid existing) new-unit-eid)))
      {:type :draft/update-error :message "Variant must belong to the same unit family."}

      (nil? (get unit-by-eid effective-eid))
      {:type :draft/update-error :message "Unit not found in this faction's roster."}

      :else
      (let [unit          (get unit-by-eid effective-eid)
            new-total     (compute-unit-total-cost unit selections conn)
            ;; Reduced army: the entry under edit is removed so validate-add
            ;; re-runs as if the unit were being freshly added. For the common
            ;; "same unit, new mount" case this keeps unit-copy counts correct.
            reduced-state (update state section-k
                                  (fn [xs]
                                    (into [] (concat (subvec xs 0 index) (subvec xs (inc index))))))
            army-entries  (concat
                           (keep (fn [e] (when-let [u (get unit-by-eid (:unit-eid e))] (assoc u :section "main")))
                                 (:main reduced-state))
                           (keep (fn [e] (when-let [u (get unit-by-eid (:unit-eid e))] (assoc u :section "reinforcements")))
                                 (:reinforcements reduced-state)))
            section-cost  (reduce (fn [s entry]
                                    (if-let [u (get unit-by-eid (:unit-eid entry))]
                                      (+ s (or (:total-cost entry) (:cost u) 0))
                                      s))
                                  0
                                  (get reduced-state section-k []))
            violation     (rules.draft/validate-add
                           army-entries unit section
                           section-cost section-max new-total)]
        (if violation
          (assoc violation :type :draft/update-error)
          (let [new-entry {:entry-eid  entry-eid
                           :unit-eid   effective-eid
                           :mount      (:mount selections)
                           :level      (or (:level selections) 0)
                           :abilities  (or (:abilities selections) [])
                           :spells     (or (:spells selections) [])
                           :items      (or (:items selections) [])
                           :total-cost new-total}
                new-state (assoc state section-k
                                 (assoc (vec section-list) index new-entry))]
            (set-draft-state dependencies draft-eid new-state)
            (let [section-ctx (hydrated-section-context conn unit-by-eid new-state section draft-eid game-mode)]
              {:type              :draft/update-success
               :entry-eid         entry-eid
               :total-cost        new-total
               :slot-portrait-key (str effective-eid)
               :budget            (section-budget section-ctx)})))))))
