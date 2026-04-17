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

(defn get-draft-by-eid
  "Fetches a draft by eid and attaches :type :game/draft."
  [dependencies eid]
  (assoc (db/get-draft-by-eid (:connection dependencies) eid) :type :game/draft))

(defn create-draft
  "Creates a new draft, stamping :created-at and :updated-at, and attaches :type :game/draft."
  [dependencies create-specification]
  (let [created-at (Instant/now)
        updated-at created-at]
    (assoc (db/create-draft (:connection dependencies)
                            (-> create-specification
                                (assoc :created-at created-at)
                                (assoc :updated-at updated-at)))
           :type :game/draft)))

(defn get-drafts-for-player
  "Returns all drafts for a player, each tagged with :type :game/draft."
  [dependencies player-sub]
  (mapv (fn [draft] (assoc draft :type :game/draft))
        (db/get-drafts-for-player (:connection dependencies) player-sub)))

(defn get-drafts-for-player-by-game
  "Returns all drafts for a player scoped to a specific game, each tagged with :type :game/draft."
  [dependencies player-sub game-eid]
  (mapv (fn [draft] (assoc draft :type :game/draft))
        (db/get-drafts-for-player-by-game (:connection dependencies) player-sub game-eid)))

(def ^:private state-entry-schema
  (m/schema
   [:map
    [:entry-eid  {:optional true} [:maybe :uuid]]
    [:unit-eid :uuid]
    [:mount      {:optional true} [:maybe :string]]
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

;; ─── Cost calculation ─────────────────────────────────────────────────────────

(defn- compute-unit-total-cost
  "Returns base-cost + mount-cost + ability-cost + spell-cost + item-cost.
   selections: {:mount str-or-nil :abilities [ability-key] :spells [spell-key] :items [item-key]}
   :mount is the mount `type` key (e.g. \"wh2_dlc09_anc_mount_skeleton_chariot\"),
   not a display name. Cost comes from unit_mount.cost via get-mounts-for-unit."
  [unit-hydrated selections conn]
  (let [base-cost    (or (:cost unit-hydrated) 0)
        mount-key    (:mount selections)
        mount-cost   (when mount-key
                       (:cost (first (filter #(= mount-key (:key %))
                                             (db/get-mounts-for-unit conn (:eid unit-hydrated))))))
        ability-keys (not-empty (:abilities selections []))
        ability-cost (when ability-keys
                       (->> (db/get-abilities-by-keys conn ability-keys)
                            vals
                            (map #(or (:cost %) 0))
                            (reduce + 0)))
        spell-keys   (not-empty (:spells selections []))
        spell-cost   (when spell-keys
                       (->> (db/get-spells-by-keys conn spell-keys)
                            vals
                            (map #(or (:cost %) 0))
                            (reduce + 0)))
        item-keys    (not-empty (set (:items selections [])))
        item-cost    (when item-keys
                       (->> (db/get-items-for-unit conn (:eid unit-hydrated))
                            (filter #(item-keys (:key %)))
                            (map #(or (:cost %) 0))
                            (reduce + 0)))]
    (+ base-cost (or mount-cost 0) (or ability-cost 0) (or spell-cost 0) (or item-cost 0))))

;; ─── Shared helpers ───────────────────────────────────────────────────────────

(defn- faction-unit-index
  "Returns a map of unit-eid → hydrated-unit for all units in the draft's faction."
  [conn faction-eid]
  (into {} (map (juxt :eid identity) (hydrate-units-with-stats (db/get-units-for-faction conn faction-eid)))))

(defn- hydrate-section
  "Resolves state entries to their hydrated units, attaching :total-cost and
   the entry's :entry-eid so templates can target the specific placed copy."
  [unit-by-eid entries]
  (mapv (fn [entry]
          (when-let [u (get unit-by-eid (:unit-eid entry))]
            (assoc u
                   :total-cost (or (:total-cost entry) (:cost u))
                   :entry-eid  (:entry-eid entry))))
        entries))

(defn- hydrated-section-context
  "Hydrates `state`'s entries for `section` and wraps them in a
   build-section-context result."
  [unit-by-eid state section draft-eid game-mode]
  (build-section-context section
                         (hydrate-section unit-by-eid (get state (keyword section) []))
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
  [unit entry-eid total-cost]
  {:eid        (:eid unit)
   :entry-eid  entry-eid
   :name       (:name unit)
   :total-cost total-cost
   :is-lord    (boolean (:is-lord unit))})

;; ─── Composite domain operations ──────────────────────────────────────────────

(defn get-draft-unit-details
  "Returns a flat :draft/unit resource for a unit in the context of a draft:
   unit identity/stats/abilities merged with the per-draft option catalog
   (items, mounts, draftable spells, passive spells) and a flag for whether
   reinforcements are enabled for the enclosing game mode.

   Abilities and spells are split into two groups by cost:
     - passive (cost = 0): always on the character; shown in a readonly section
     - draftable (cost > 0): purchasable options shown in a selectable section"
  [dependencies draft-eid unit-eid]
  (let [conn                                                                 (:connection dependencies)
        draft                                                                (db/get-draft-by-eid conn draft-eid)
        game-mode                                                            (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        unit                                                                 (db/get-unit-by-eid conn unit-eid)
        {:keys [stats health barrier abilities draftable-spells attributes]} (parse-unit-statistics (:unit-statistics unit))
        unit-statistics                                                      (mapv add-stat-percentage stats)
        ability-by-key                                                       (db/get-abilities-by-keys conn abilities)
        all-abilities                                                        (into []
                                                                                   (keep (fn [k]
                                                                                           (when-let [{:keys [name eid description cost]} (get ability-by-key k)]
                                                                                             {:key k :name name :eid eid :description description :cost (or cost 0)})))
                                                                                   abilities)
        passive-abilities                                                    (filterv #(= 0 (:cost %)) all-abilities)
        draftable-abilities                                                  (filterv #(pos? (:cost %)) all-abilities)
        spell-by-key                                                         (db/get-spells-by-keys conn draftable-spells)
        all-spells                                                           (into []
                                                                                   (keep (fn [k]
                                                                                           (when-let [{:keys [eid name mana-cost cost]} (get spell-by-key k)]
                                                                                             {:key k :eid eid :name name :mana-cost (or mana-cost 0) :cost (or cost 0)})))
                                                                                   draftable-spells)
        passive-spells                                                       (filterv #(= 0 (:cost %)) all-spells)
        draftable-spells-v                                                   (filterv #(pos? (:cost %)) all-spells)
        items                                                                (db/get-items-for-unit conn unit-eid)
        mounts                                                               (db/get-mounts-for-unit conn unit-eid)]
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
           :validation          {:can-add-to-reinforcements? (= 1 (:reinforcements-enabled game-mode))})))

(defn- mark-selected
  "Returns options with :selected true/false set from whether each :key is in selected-keys."
  [options selected-keys]
  (mapv (fn [opt] (assoc opt :selected (contains? selected-keys (:key opt)))) options))

(declare get-draft-entry)

(defn get-draft-entry-details
  "Returns a truly slim :draft/entry resource for a placed entry: just
   addressing fields (eid, draft-eid, unit-eid, section) and the selection
   state the player has stored on the entry as raw key lists (:mount,
   :abilities, :spells, :items). Clients that want the unit's catalog
   fetch it via `?embed=unit`, which runs embed-unit-for-entry."
  [dependencies draft-eid entry-eid section]
  (when-let [entry (get-draft-entry dependencies draft-eid entry-eid section)]
    {:type      :draft/entry
     :eid       (:entry-eid entry)
     :draft-eid draft-eid
     :unit-eid  (:unit-eid entry)
     :section   section
     :mount     (:mount entry)
     :abilities (vec (:abilities entry))
     :spells    (vec (:spells entry))
     :items     (vec (:items entry))}))

(defn embed-unit-for-entry
  "Embed function for the `unit` embed on a draft-entry-resource. Loads the
   draft-scoped unit resource for the entry's unit-eid and marks :selected
   on draftable abilities, draftable spells, items, and mounts based on the
   entry's stored selection key lists, then assocs the result under
   [:_embedded :unit]."
  [dependencies entry-resource]
  (let [ability-set (set (:abilities entry-resource))
        spell-set   (set (:spells entry-resource))
        item-set    (set (:items entry-resource))
        unit        (-> (get-draft-unit-details dependencies
                                                (:draft-eid entry-resource)
                                                (:unit-eid entry-resource))
                        (update :draftable-abilities mark-selected ability-set)
                        (update :draftable-spells mark-selected spell-set)
                        (update :items mark-selected item-set))]
    (assoc-in entry-resource [:_embedded :unit] unit)))

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
                                       :abilities  (or (:abilities selections) [])
                                       :spells     (or (:spells selections) [])
                                       :items      (or (:items selections) [])
                                       :total-cost total-cost})]
            (set-draft-state dependencies draft-eid new-state)
            (let [section-ctx (hydrated-section-context unit-by-eid new-state section draft-eid game-mode)]
              {:type     :draft/add-success
               :section  (section-ref section-ctx)
               :new-unit (slot-unit unit new-entry-eid total-cost)
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
        idx           (find-entry-index old-list entry-eid)
        removed-entry (when idx (nth old-list idx))
        unit-by-eid   (faction-unit-index conn (:faction-eid draft))
        removed-unit  (when removed-entry (get unit-by-eid (:unit-eid removed-entry)))
        new-state     (assoc state section-k
                             (if (some? idx)
                               (into [] (concat (subvec old-list 0 idx) (subvec old-list (inc idx))))
                               old-list))]
    (set-draft-state dependencies draft-eid new-state)
    (let [section-ctx (hydrated-section-context unit-by-eid new-state section draft-eid game-mode)]
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

(defn update-unit-in-draft
  "Validates and replaces the selections of an already-placed draft entry.
   entry-eid identifies the specific state entry; section says which list it
   lives in. selections has the same shape as add-unit-to-draft selections.
   Returns {:type :draft/update-success …} on success or
   {:type :draft/update-error …} on violation or missing entry."
  [dependencies draft-eid entry-eid section selections]
  (let [selections   (update selections :mount not-empty)
        conn         (:connection dependencies)
        draft        (db/get-draft-by-eid conn draft-eid)
        game-mode    (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        state        (get-draft-state dependencies draft-eid)
        section-k    (keyword section)
        section-list (get state section-k [])
        idx          (find-entry-index section-list entry-eid)
        existing     (when idx (nth section-list idx))
        unit-by-eid  (faction-unit-index conn (:faction-eid draft))
        section-max  (if (= section "main")
                       (:draft-value game-mode)
                       (:reinforcement-value game-mode))]
    (cond
      (nil? existing)
      {:type :draft/update-error :message "Unit not found in this section."}

      (nil? (get unit-by-eid (:unit-eid existing)))
      {:type :draft/update-error :message "Unit not found in this faction's roster."}

      :else
      (let [unit          (get unit-by-eid (:unit-eid existing))
            new-total     (compute-unit-total-cost unit selections conn)
            ;; Reduced army: the entry under edit is removed so validate-add
            ;; re-runs as if the unit were being freshly added. For the common
            ;; "same unit, new mount" case this keeps unit-copy counts correct.
            reduced-state (update state section-k
                                  (fn [xs]
                                    (into [] (concat (subvec xs 0 idx) (subvec xs (inc idx))))))
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
                           :unit-eid   (:unit-eid existing)
                           :mount      (:mount selections)
                           :abilities  (or (:abilities selections) [])
                           :spells     (or (:spells selections) [])
                           :items      (or (:items selections) [])
                           :total-cost new-total}
                new-state (assoc state section-k
                                 (assoc (vec section-list) idx new-entry))]
            (set-draft-state dependencies draft-eid new-state)
            (let [section-ctx (hydrated-section-context unit-by-eid new-state section draft-eid game-mode)]
              {:type       :draft/update-success
               :entry-eid  entry-eid
               :total-cost new-total
               :budget     (section-budget section-ctx)})))))))
