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

(def ^:private stat-exclude-keys #{"abilities" "draftable-spells" "draftable-abilities" "mounts" "equipment"})

(defn- stat-entry
  "Converts a single decoded JSON entry into a stat map, or nil if it should be excluded.
   Drops structured fields (abilities, mounts, etc.), zero values, and empty vectors."
  [[k v]]
  (when-not (stat-exclude-keys k)
    (cond
      (and (vector? v) (empty? v)) nil
      (= v 0)                      nil
      (vector? v)                  {:stat (str/replace k "_" " ") :value (str/join ", " v)}
      :else                        {:stat (str/replace k "_" " ") :value v})))

(defn parse-unit-statistics
  "Parses a unit-statistics JSON string into a structured map with keys:
     :stats            — vector of {:stat name :value val} for numeric/list fields
     :abilities        — vector of ability name strings
     :draftable-spells — vector of spell key strings
     :mounts           — vector of {:name str :mp-cost int}
     :equipment        — vector of raw equipment maps"
  [unit-statistics-str]
  (let [decoded (m/decode db/unit-statistics-raw-schema
                          (jsonista/read-value unit-statistics-str (jsonista/object-mapper {:decode-key-fn name}))
                          db/unit-statistics-transformer)]
    {:stats
     (into [] (keep stat-entry) decoded)
     :abilities        (get decoded "abilities")
     :draftable-spells (get decoded "draftable-spells")
     :mounts           (mapv (fn [m] {:name (get m "name")
                                      :cost (get m "cost")})
                             (get decoded "mounts"))
     :equipment        (get decoded "equipment")}))

;; ─── Stat percentages ─────────────────────────────────────────────────────────

(def ^:private stat-max-values
  {"armor"           100.0
   "armour"          100.0
   "leadership"      100.0
   "speed"            60.0
   "melee attack"     60.0
   "melee defence"    60.0
   "weapon strength" 700.0
   "charge bonus"     60.0
   "missile damage"  300.0
   "health"         1000.0
   "barrier"        1000.0})

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
    [:unit-eid :uuid]
    [:mount {:optional true} [:maybe :string]]
    [:spells {:optional true, :default []} [:sequential :string]]
    [:items  {:optional true, :default []} [:sequential :string]]
    [:total-cost {:optional true} [:maybe :int]]]))

(def ^:private state-entry-transformer
  (mt/transformer mt/string-transformer
                  (mt/default-value-transformer {::mt/add-optional-keys true})))

(defn- parse-state-entry
  "Decodes a raw JSON-decoded state entry into a typed map via Malli.
   Converts :unit-eid string to UUID and defaults :spells/:items to []."
  [entry]
  (m/decode state-entry-schema entry state-entry-transformer))

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
   Each entry: {:unit-eid uuid :mount str-or-nil :spells [str] :items [str] :total-cost int-or-nil}."
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

;; ─── Cost calculation ─────────────────────────────────────────────────────────

(defn- compute-unit-total-cost
  "Returns base-cost + mount-cost + spell-cost + item-cost.
   selections: {:mount str-or-nil :spells [spell-key] :items [item-key]}"
  [unit-hydrated selections conn]
  (let [parsed-stats (parse-unit-statistics (:unit-statistics unit-hydrated))
        base-cost    (or (:cost unit-hydrated) 0)
        mount-name   (:mount selections)
        mount-cost   (when mount-name
                       (:cost (first (filter #(= mount-name (:name %))
                                             (:mounts parsed-stats)))))
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
    (+ base-cost (or mount-cost 0) (or spell-cost 0) (or item-cost 0))))

;; ─── Shared helpers ───────────────────────────────────────────────────────────

(defn- faction-unit-index
  "Returns a map of unit-eid → hydrated-unit for all units in the draft's faction."
  [conn faction-eid]
  (into {} (map (juxt :eid identity) (hydrate-units-with-stats (db/get-units-for-faction conn faction-eid)))))

(defn- hydrate-section
  "Resolves state entries to their hydrated units, attaching :total-cost from the entry."
  [unit-by-eid entries]
  (mapv (fn [entry]
          (when-let [u (get unit-by-eid (:unit-eid entry))]
            (assoc u :total-cost (or (:total-cost entry) (:cost u)))))
        entries))

(defn- build-section-mutation-response
  "Builds the add/remove success response map containing updated section contexts.
   :oob rendering is handled by the HTMX encoder, not here."
  [type unit-by-eid new-state draft-eid game-mode reinf-enabled]
  (let [main-ctx  (build-section-context "main"
                                         (hydrate-section unit-by-eid (:main new-state))
                                         draft-eid game-mode)
        reinf-ctx (build-section-context "reinforcements"
                                         (hydrate-section unit-by-eid (:reinforcements new-state))
                                         draft-eid game-mode)]
    {:type          type
     :main-section  main-ctx
     :reinf-section (when reinf-enabled reinf-ctx)}))

;; ─── Composite domain operations ──────────────────────────────────────────────

(defn get-draft-unit-details
  "Returns a :draft/unit response map for a unit in the context of a draft, including
   parsed stat percentages, resolved ability descriptions, available items, and whether
   reinforcements are enabled."
  [dependencies draft-eid unit-eid]
  (let [conn             (:connection dependencies)
        draft            (db/get-draft-by-eid conn draft-eid)
        game-mode        (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        unit             (db/get-unit-by-eid conn unit-eid)
        {:keys [stats abilities]} (parse-unit-statistics (:unit-statistics unit))
        unit-statistics  (mapv add-stat-percentage stats)
        ability-by-key   (db/get-abilities-by-keys conn abilities)
        parsed-abilities (into []
                               (keep (fn [k]
                                       (when-let [{:keys [name eid description]} (get ability-by-key k)]
                                         {:name name :eid eid :description description})))
                               abilities)
        items            (db/get-items-for-unit conn unit-eid)]
    {:type                   :draft/unit
     :unit                   (assoc unit
                                    :unit-statistics unit-statistics
                                    :parsed-abilities parsed-abilities)
     :items                  items
     :draft-eid              draft-eid
     :reinforcements-enabled (= 1 (:reinforcements-enabled game-mode))}))

(defn add-unit-to-draft
  "Validates and adds a unit to the specified section of a draft.
   selections: {:mount str-or-nil :spells [spell-key] :items [item-key]}
   Returns {:type :draft/add-success ...} on success, {:type :draft/add-error ...} on violation."
  [dependencies draft-eid unit-eid section selections]
  (let [conn          (:connection dependencies)
        draft         (db/get-draft-by-eid conn draft-eid)
        game-mode     (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        state         (get-draft-state dependencies draft-eid)
        section-k     (keyword section)
        unit-by-eid   (faction-unit-index conn (:faction-eid draft))
        unit          (get unit-by-eid unit-eid)
        reinf-enabled (= 1 (:reinforcements-enabled game-mode))
        section-max   (if (= section "main")
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
          (let [new-state (update state section-k (fnil conj [])
                                  {:unit-eid   unit-eid
                                   :mount      (:mount selections)
                                   :spells     (or (:spells selections) [])
                                   :items      (or (:items selections) [])
                                   :total-cost total-cost})]
            (set-draft-state dependencies draft-eid new-state)
            (build-section-mutation-response :draft/add-success unit-by-eid new-state draft-eid game-mode reinf-enabled)))))))

(defn remove-unit-from-draft
  "Removes the first occurrence of unit-eid from the given section of a draft's state
   and returns a :draft/remove-success response map."
  [dependencies draft-eid unit-eid section]
  (let [conn          (:connection dependencies)
        draft         (db/get-draft-by-eid conn draft-eid)
        game-mode     (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        state         (get-draft-state dependencies draft-eid)
        section-k     (keyword section)
        old-list      (get state section-k [])
        idx           (first (keep-indexed
                              (fn [i entry] (when (= unit-eid (:unit-eid entry)) i))
                              old-list))
        new-state     (assoc state section-k
                             (if (some? idx)
                               (into [] (concat (subvec old-list 0 idx) (subvec old-list (inc idx))))
                               old-list))
        reinf-enabled (= 1 (:reinforcements-enabled game-mode))
        unit-by-eid   (faction-unit-index conn (:faction-eid draft))]
    (set-draft-state dependencies draft-eid new-state)
    (build-section-mutation-response :draft/remove-success unit-by-eid new-state draft-eid game-mode reinf-enabled)))
