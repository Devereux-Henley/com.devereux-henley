(ns com.devereux-henley.rts-domain.handlers.draft
  (:require
   [clojure.string :as str]
   [com.devereux-henley.rts-data-access.contract :as db]
   [jsonista.core :as jsonista])
  (:import
   [java.time Instant]))

;; ─── Unit statistics parsing ──────────────────────────────────────────────────

(def ^:private stat-exclude-keys #{"abilities" "draftable-spells" "draftable-abilities" "mounts" "equipment"})

(defn parse-unit-statistics
  [unit-statistics-str]
  (try
    (let [stats (jsonista/read-value unit-statistics-str (jsonista/object-mapper {:decode-key-fn name}))]
      {:stats
       (into []
             (keep (fn [[k v]]
                     (when-not (stat-exclude-keys k)
                       (cond
                         (and (vector? v) (empty? v)) nil
                         (= v 0)                      nil
                         (vector? v)                  {:stat (str/replace k "_" " ") :value (str/join ", " v)}
                         :else                        {:stat (str/replace k "_" " ") :value v}))))
             stats)
       :abilities        (get stats "abilities" [])
       :draftable-spells (get stats "draftable-spells" [])
       :mounts           (mapv (fn [m] {:name    (get m "name")
                                        :mp-cost (get m "mp_cost")})
                               (get stats "mounts" []))
       :equipment        (get stats "equipment" [])})
    (catch Exception _
      {:stats [] :abilities [] :draftable-spells [] :mounts [] :equipment []})))

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
  [{:keys [stat value] :as s}]
  (let [max-val (get stat-max-values (str/lower-case (str stat)) 100.0)
        raw-val (cond
                  (number? value) (double value)
                  (string? value) (try (Double/parseDouble value) (catch Exception _ 0.0))
                  :else 0.0)]
    (assoc s :percentage (int (min 100 (Math/round (* 100.0 (/ raw-val max-val))))))))

;; ─── Section context ──────────────────────────────────────────────────────────

(defn- section-percentage [cost max-val]
  (if (and max-val (pos? max-val))
    (int (min 100 (Math/round (double (* 100 (/ cost max-val))))))
    0))

(defn hydrate-units-with-stats
  [units]
  (mapv (fn [u]
          (let [{:keys [stats abilities]} (parse-unit-statistics (:unit-statistics u))]
            (assoc u
                   :parsed-stats stats
                   :parsed-abilities abilities
                   :is-lord (= "Lord" (:unit-category-name u)))))
        units))

(defn build-section-context
  [section units draft-eid game-mode]
  (let [is-main       (= section "main")
        section-label (if is-main "Main Army" "Reinforcements")
        section-id    (if is-main "main-army-section" "reinforcements-section")
        section-max   (if is-main (:draft-value game-mode) (:reinforcement-value game-mode))
        cost          (reduce (fn [s u] (+ s (or (:cost u) 0))) 0 units)
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
     :section-over-budget (> cost section-max)
     :draft-eid           draft-eid}))

;; ─── Draft state ──────────────────────────────────────────────────────────────

(defn get-draft-by-eid
  [dependencies eid]
  (assoc (db/get-draft-by-eid (:connection dependencies) eid) :type :game/draft))

(defn create-draft
  [dependencies create-specification]
  (let [created-at (Instant/now)
        updated-at created-at]
    (assoc (db/create-draft (:connection dependencies)
                            (-> create-specification
                                (assoc :created-at created-at)
                                (assoc :updated-at updated-at)))
           :type :game/draft)))

(defn get-drafts-for-player
  [dependencies player-sub]
  (mapv (fn [draft] (assoc draft :type :game/draft))
        (db/get-drafts-for-player (:connection dependencies) player-sub)))

(defn get-drafts-for-player-by-game
  [dependencies player-sub game-eid]
  (mapv (fn [draft] (assoc draft :type :game/draft))
        (db/get-drafts-for-player-by-game (:connection dependencies) player-sub game-eid)))

(defn get-draft-state
  "Returns {:main [uuid ...] :reinforcements [uuid ...]} parsed from the stored JSON blob.
   Returns empty collections if no state exists yet."
  [dependencies draft-eid]
  (if-let [row (db/get-draft-state-by-draft (:connection dependencies) draft-eid)]
    (let [parsed (jsonista/read-value (:state row) (jsonista/object-mapper {:decode-key-fn keyword}))]
      {:main           (mapv #(java.util.UUID/fromString %) (get parsed :main []))
       :reinforcements (mapv #(java.util.UUID/fromString %) (get parsed :reinforcements []))})
    {:main [] :reinforcements []}))

(defn set-draft-state
  "Persists {:main [uuid ...] :reinforcements [uuid ...]} as an atomic JSON blob."
  [dependencies draft-eid state]
  (let [json-str (jsonista/write-value-as-string
                  {:main           (mapv str (:main state))
                   :reinforcements (mapv str (:reinforcements state))})]
    (db/upsert-draft-state (:connection dependencies) draft-eid json-str)))

(defn get-spells-by-keys
  [dependencies spell-keys]
  (db/get-spells-by-keys (:connection dependencies) spell-keys))

(defn get-abilities-by-names
  [dependencies ability-names]
  (db/get-abilities-by-names (:connection dependencies) ability-names))

;; ─── Composite domain operations ──────────────────────────────────────────────

(defn get-draft-unit-details
  "Returns the full unit response for a unit in the context of a draft,
   including stat percentages and resolved abilities."
  [dependencies draft-eid unit-eid]
  (let [draft            (get-draft-by-eid dependencies draft-eid)
        game-mode        (db/get-game-mode-by-eid (:connection dependencies) (:game-mode-eid draft))
        unit             (db/get-unit-by-eid (:connection dependencies) unit-eid)
        {:keys [stats abilities]} (parse-unit-statistics (:unit-statistics unit))
        unit-statistics  (mapv add-stat-percentage stats)
        ability-by-name  (db/get-abilities-by-names (:connection dependencies) abilities)
        parsed-abilities (mapv (fn [a]
                                 (let [{:keys [eid description]} (get ability-by-name a)]
                                   {:name a :eid eid :description description}))
                               abilities)]
    {:type                   :draft/unit
     :unit                   (assoc unit
                                    :unit-statistics unit-statistics
                                    :parsed-abilities parsed-abilities)
     :draft-eid              draft-eid
     :reinforcements-enabled (= 1 (:reinforcements-enabled game-mode))}))

(defn add-unit-to-draft
  "Validates and adds a unit to the specified section of a draft.
   Returns a success body or an error body with :http-status."
  [dependencies draft-eid unit-eid section]
  (let [draft         (get-draft-by-eid dependencies draft-eid)
        game-mode     (db/get-game-mode-by-eid (:connection dependencies) (:game-mode-eid draft))
        state         (get-draft-state dependencies draft-eid)
        section-k     (keyword section)
        all-units     (hydrate-units-with-stats
                       (db/get-units-for-faction (:connection dependencies) (:faction-eid draft)))
        unit-by-eid   (into {} (map (juxt :eid identity) all-units))
        hydrate       (fn [eids] (vec (keep unit-by-eid eids)))
        units-in      (hydrate (get state section-k []))
        section-max   (if (= section "main") (:draft-value game-mode) (:reinforcement-value game-mode))
        cost-so-far   (reduce (fn [s u] (+ s (or (:cost u) 0))) 0 units-in)
        unit-hydrated (first (filter #(= unit-eid (:eid %)) all-units))
        unit-cost     (or (:cost unit-hydrated) 0)
        total-cost    (+ cost-so-far unit-cost)
        is-lord       (:is-lord unit-hydrated)
        lords-in      (count (filter :is-lord units-in))
        reinf-enabled (= 1 (:reinforcements-enabled game-mode))]
    (cond
      (nil? unit-hydrated)
      {:type :draft/add-error
       :message "Unit not found in this faction's roster."
       :http-status 422}

      (> total-cost section-max)
      {:type :draft/add-error
       :message (str "Adding " (:name unit-hydrated)
                     " would exceed the " section
                     " army budget of " section-max ".")
       :http-status 422}

      (and is-lord (> lords-in 0))
      {:type :draft/add-error
       :message "Only one lord may be added to an army section."
       :http-status 422}

      :else
      (let [new-state  (update state section-k (fnil conj []) unit-eid)
            main-ctx   (build-section-context "main" (hydrate (:main new-state)) draft-eid game-mode)
            reinf-ctx  (build-section-context "reinforcements" (hydrate (:reinforcements new-state)) draft-eid game-mode)]
        (set-draft-state dependencies draft-eid new-state)
        {:type          :draft/add-success
         :main-section  main-ctx
         :reinf-section (when reinf-enabled reinf-ctx)}))))

(defn remove-unit-from-draft
  "Removes the first occurrence of unit-eid from the specified section of a draft.
   Returns a success body."
  [dependencies draft-eid unit-eid section]
  (let [draft         (get-draft-by-eid dependencies draft-eid)
        game-mode     (db/get-game-mode-by-eid (:connection dependencies) (:game-mode-eid draft))
        state         (get-draft-state dependencies draft-eid)
        section-k     (keyword section)
        old-list      (get state section-k [])
        idx           (.indexOf old-list unit-eid)
        new-list      (if (>= idx 0)
                        (into [] (concat (subvec old-list 0 idx)
                                         (subvec old-list (inc idx))))
                        old-list)
        new-state     (assoc state section-k new-list)
        reinf-enabled (= 1 (:reinforcements-enabled game-mode))]
    (set-draft-state dependencies draft-eid new-state)
    (let [all-units   (hydrate-units-with-stats
                       (db/get-units-for-faction (:connection dependencies) (:faction-eid draft)))
          unit-by-eid (into {} (map (juxt :eid identity) all-units))
          hydrate     (fn [eids] (vec (keep unit-by-eid eids)))
          main-ctx    (build-section-context "main" (hydrate (:main new-state)) draft-eid game-mode)
          reinf-ctx   (build-section-context "reinforcements" (hydrate (:reinforcements new-state)) draft-eid game-mode)]
      {:type          :draft/remove-success
       :main-section  main-ctx
       :reinf-section (when reinf-enabled reinf-ctx)})))
