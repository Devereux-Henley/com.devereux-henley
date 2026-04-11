(ns com.devereux-henley.rts-domain.handlers.draft
  (:require
   [clojure.string :as str]
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.rules.draft :as rules.draft]
   [jsonista.core :as jsonista])
  (:import
   [java.time Instant]))

;; ─── Unit statistics parsing ──────────────────────────────────────────────────

(def ^:private stat-exclude-keys #{"abilities" "draftable-spells" "draftable-abilities" "mounts" "equipment" "is_unique"})

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
       :equipment        (get stats "equipment" [])
       :is-unique        (if (contains? stats "is_unique")
                           (boolean (get stats "is_unique"))
                           (boolean (seq (get stats "equipment" []))))})
    (catch Exception _
      {:stats [] :abilities [] :draftable-spells [] :mounts [] :equipment [] :is-unique false})))

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
          (let [{:keys [stats abilities is-unique]} (parse-unit-statistics (:unit-statistics u))]
            (assoc u
                   :parsed-stats stats
                   :parsed-abilities abilities
                   :is-lord (= "Lord" (:unit-category-name u))
                   :is-unique is-unique)))
        units))

(defn build-section-context
  [section units draft-eid game-mode]
  (let [is-main       (= section "main")
        section-label (if is-main "Main Army" "Reinforcements")
        section-id    (if is-main "main-army-section" "reinforcements-section")
        section-max   (if is-main (:draft-value game-mode) (:reinforcement-value game-mode))
        ;; Use :total-cost (set from draft state) when present; fall back to base :cost.
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

(defn- parse-state-entry
  "Normalises a draft-state entry into the canonical map form.
   Handles the legacy format where entries were plain UUID strings."
  [entry]
  (if (string? entry)
    {:unit-eid   (java.util.UUID/fromString entry)
     :mount      nil
     :spells     []
     :items      []
     :total-cost nil}
    {:unit-eid   (java.util.UUID/fromString (str (:unit-eid entry)))
     :mount      (:mount entry)
     :spells     (or (:spells entry) [])
     :items      (or (:items entry) [])
     :total-cost (:total-cost entry)}))

(defn get-draft-state
  "Returns {:main [entry …] :reinforcements [entry …]} where each entry is
   {:unit-eid uuid :mount str-or-nil :spells [str] :items [str] :total-cost int-or-nil}.
   Returns empty collections when no state exists.
   Handles the legacy format where entries were plain UUID strings."
  [dependencies draft-eid]
  (if-let [row (db/get-draft-state-by-draft (:connection dependencies) draft-eid)]
    (let [parsed (jsonista/read-value (:state row) (jsonista/object-mapper {:decode-key-fn keyword}))]
      {:main           (mapv parse-state-entry (get parsed :main []))
       :reinforcements (mapv parse-state-entry (get parsed :reinforcements []))})
    {:main [] :reinforcements []}))

(defn set-draft-state
  "Persists {:main [entry …] :reinforcements [entry …]} as an atomic JSON blob.
   Each entry: {:unit-eid uuid :mount str-or-nil :spells [str] :items [str] :total-cost int-or-nil}."
  [dependencies draft-eid state]
  (let [serialise-entry (fn [entry]
                          {:unit-eid   (str (:unit-eid entry))
                           :mount      (:mount entry)
                           :spells     (or (:spells entry) [])
                           :items      (or (:items entry) [])
                           :total-cost (:total-cost entry)})
        json-str (jsonista/write-value-as-string
                  {:main           (mapv serialise-entry (:main state))
                   :reinforcements (mapv serialise-entry (:reinforcements state))})]
    (db/upsert-draft-state (:connection dependencies) draft-eid json-str)))

(defn get-spells-by-keys
  [dependencies spell-keys]
  (db/get-spells-by-keys (:connection dependencies) spell-keys))

(defn get-abilities-by-names
  [dependencies ability-names]
  (db/get-abilities-by-names (:connection dependencies) ability-names))

;; ─── Cost calculation ─────────────────────────────────────────────────────────

(defn- compute-unit-total-cost
  "Returns base-cost + mount-cost + spell-cost + item-cost.
   selections: {:mount str-or-nil :spells [spell-key] :items [item-key]}"
  [unit-hydrated selections conn]
  (let [parsed-stats (parse-unit-statistics (:unit-statistics unit-hydrated))
        base-cost    (or (:cost unit-hydrated) 0)
        mount-name   (:mount selections)
        mount-cost   (when mount-name
                       (:mp-cost (first (filter #(= mount-name (:name %))
                                                (:mounts parsed-stats)))))
        spell-keys   (not-empty (:spells selections []))
        spell-cost   (when spell-keys
                       (->> (db/get-spells-by-keys conn spell-keys)
                            vals
                            (map #(or (:gold-cost %) 0))
                            (reduce + 0)))
        item-keys    (not-empty (set (:items selections [])))
        item-cost    (when item-keys
                       (->> (:equipment parsed-stats)
                            (filter #(item-keys (get % "key")))
                            (map #(or (get % "gold_cost") 0))
                            (reduce + 0)))]
    (+ base-cost (or mount-cost 0) (or spell-cost 0) (or item-cost 0))))

;; ─── Composite domain operations ──────────────────────────────────────────────

(defn get-draft-unit-details
  [dependencies draft-eid unit-eid]
  (let [conn             (:connection dependencies)
        draft            (db/get-draft-by-eid conn draft-eid)
        game-mode        (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        unit             (db/get-unit-by-eid conn unit-eid)
        {:keys [stats abilities]} (parse-unit-statistics (:unit-statistics unit))
        unit-statistics  (mapv add-stat-percentage stats)
        ability-by-name  (db/get-abilities-by-names conn abilities)
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
   selections: {:mount str-or-nil :spells [spell-key] :items [item-key]}
   Returns {:type :draft/add-success ...} on success, {:type :draft/add-error ...} on violation."
  [dependencies draft-eid unit-eid section selections]
  (let [conn          (:connection dependencies)
        draft         (db/get-draft-by-eid conn draft-eid)
        game-mode     (db/get-game-mode-by-eid conn (:game-mode-eid draft))
        state         (get-draft-state dependencies draft-eid)
        section-k     (keyword section)
        all-units     (hydrate-units-with-stats (db/get-units-for-faction conn (:faction-eid draft)))
        unit-by-eid   (into {} (map (juxt :eid identity) all-units))
        unit-hydrated (get unit-by-eid unit-eid)
        reinf-enabled (= 1 (:reinforcements-enabled game-mode))
        section-max   (if (= section "main")
                        (:draft-value game-mode)
                        (:reinforcement-value game-mode))]
    (if (nil? unit-hydrated)
      {:type :draft/add-error :message "Unit not found in this faction's roster."}
      (let [total-cost    (compute-unit-total-cost unit-hydrated selections conn)
            ;; Build flat army entries (both sections) for rule evaluation.
            build-entries (fn [entries sec]
                            (keep (fn [entry]
                                    (when-let [u (get unit-by-eid (:unit-eid entry))]
                                      (assoc u :section sec)))
                                  entries))
            army-entries  (concat
                           (build-entries (get state :main []) "main")
                           (build-entries (get state :reinforcements []) "reinforcements"))
            ;; Cost of units already in target section (from stored total-costs).
            section-cost  (reduce (fn [s entry]
                                    (if-let [u (get unit-by-eid (:unit-eid entry))]
                                      (+ s (or (:total-cost entry) (:cost u) 0))
                                      s))
                                  0
                                  (get state section-k []))
            violation     (rules.draft/validate-add
                           army-entries unit-hydrated section
                           section-cost section-max total-cost)]
        (if violation
          violation
          (let [new-entry  {:unit-eid   unit-eid
                            :mount      (:mount selections)
                            :spells     (or (:spells selections) [])
                            :items      (or (:items selections) [])
                            :total-cost total-cost}
                new-state  (update state section-k (fnil conj []) new-entry)
                hydrate-section (fn [entries]
                                  (mapv (fn [entry]
                                          (when-let [u (get unit-by-eid (:unit-eid entry))]
                                            (assoc u :total-cost (or (:total-cost entry) (:cost u)))))
                                        entries))
                main-ctx   (build-section-context "main"
                                                  (hydrate-section (:main new-state))
                                                  draft-eid game-mode)
                reinf-ctx  (build-section-context "reinforcements"
                                                  (hydrate-section (:reinforcements new-state))
                                                  draft-eid game-mode)]
            (set-draft-state dependencies draft-eid new-state)
            {:type          :draft/add-success
             :main-section  main-ctx
             :reinf-section (when reinf-enabled reinf-ctx)}))))))

(defn remove-unit-from-draft
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
        new-list      (if (some? idx)
                        (into [] (concat (subvec old-list 0 idx)
                                         (subvec old-list (inc idx))))
                        old-list)
        new-state     (assoc state section-k new-list)
        reinf-enabled (= 1 (:reinforcements-enabled game-mode))]
    (set-draft-state dependencies draft-eid new-state)
    (let [all-units   (hydrate-units-with-stats (db/get-units-for-faction conn (:faction-eid draft)))
          unit-by-eid (into {} (map (juxt :eid identity) all-units))
          hydrate-section (fn [entries]
                            (mapv (fn [entry]
                                    (when-let [u (get unit-by-eid (:unit-eid entry))]
                                      (assoc u :total-cost (or (:total-cost entry) (:cost u)))))
                                  entries))
          main-ctx    (build-section-context "main"
                                             (hydrate-section (:main new-state))
                                             draft-eid game-mode)
          reinf-ctx   (build-section-context "reinforcements"
                                             (hydrate-section (:reinforcements new-state))
                                             draft-eid game-mode)]
      {:type          :draft/remove-success
       :main-section  main-ctx
       :reinf-section (when reinf-enabled reinf-ctx)})))
