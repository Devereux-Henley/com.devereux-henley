(ns com.devereux-henley.rts-web.web.draft
  (:require
   [clojure.string :as str]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]
   [taoensso.timbre :as log]))

;; ─── Shared helpers ───────────────────────────────────────────────────────────

(defn section-percentage [cost max-val]
  (if (and max-val (pos? max-val))
    (int (min 100 (Math/round (double (* 100 (/ cost max-val))))))
    0))

(defn hydrate-units-with-stats
  [units]
  (mapv (fn [u]
          (let [{:keys [stats abilities]} (domain/parse-unit-statistics (:unit-statistics u))]
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

;; ─── Stat percentage for draft unit ───────────────────────────────────────────────────────

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

(defn ^:private add-stat-percentage
  [{:keys [stat value] :as s}]
  (let [max-val (get stat-max-values (str/lower-case (str stat)) 100.0)
        raw-val (cond
                  (number? value) (double value)
                  (string? value) (try (Double/parseDouble value) (catch Exception _ 0.0))
                  :else 0.0)]
    (assoc s :percentage (int (min 100 (Math/round (* 100.0 (/ raw-val max-val))))))))

;; ─── Handlers ─────────────────────────────────────────────────────────────────

(defmethod integrant.core/init-key ::get-draft-unit
  [_init-key dependencies]
  (fn [request]
    (try
      (let [{{{:keys [eid unit-eid]} :path} :parameters} request
            draft       (domain/get-draft-by-eid dependencies eid)
            game-mode   (domain/get-game-mode-by-eid dependencies (:game-mode-eid draft))
            unit        (domain/get-unit-by-eid dependencies unit-eid)
            {:keys [stats abilities]} (domain/parse-unit-statistics (:unit-statistics unit))
            unit-statistics  (mapv add-stat-percentage stats)
            ability-by-name  (domain/get-abilities-by-names dependencies abilities)
            parsed-abilities (mapv (fn [a]
                                     (let [{:keys [eid description]} (get ability-by-name a)]
                                       {:name        a
                                        :eid         eid
                                        :description description}))
                                   abilities)]
        {:status 200
         :body   {:type                   :draft/unit
                  :unit                   (assoc unit
                                                 :unit-statistics unit-statistics
                                                 :parsed-abilities parsed-abilities)
                  :draft-eid              eid
                  :reinforcements-enabled (= 1 (:reinforcements-enabled game-mode))}})
      (catch Exception exc
        (log/error exc)
        {:status 500
         :body   {:type    :draft/add-error
                  :message "Failed to load unit details."}}))))

(defmethod integrant.core/init-key ::draft-add-unit
  [_init-key dependencies]
  (fn [request]
    (try
      (let [{{{:keys [eid unit-eid]} :path
              {:keys [section]}      :query} :parameters} request
            draft         (domain/get-draft-by-eid dependencies eid)
            game-mode     (domain/get-game-mode-by-eid dependencies (:game-mode-eid draft))
            state         (domain/get-draft-state dependencies eid)
            section-k     (keyword section)
            all-units     (hydrate-units-with-stats
                           (domain/get-units-for-faction dependencies (:faction-eid draft)))
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
          {:status 422
           :body   {:type :draft/add-error :message "Unit not found in this faction's roster."}}

          (> total-cost section-max)
          {:status 422
           :body   {:type    :draft/add-error
                    :message (str "Adding " (:name unit-hydrated)
                                  " would exceed the " section
                                  " army budget of " section-max ".")}}

          (and is-lord (> lords-in 0))
          {:status 422
           :body   {:type :draft/add-error :message "Only one lord may be added to an army section."}}

          :else
          (let [new-state   (update state section-k (fnil conj []) unit-eid)
                main-ctx    (build-section-context "main" (hydrate (:main new-state)) eid game-mode)
                reinf-ctx   (build-section-context "reinforcements" (hydrate (:reinforcements new-state)) eid game-mode)]
            (domain/set-draft-state dependencies eid new-state)
            {:status 200
             :body   {:type          :draft/add-success
                      :main-section  (assoc main-ctx :oob true)
                      :reinf-section (when reinf-enabled (assoc reinf-ctx :oob true))}})))
      (catch Exception exc
        (log/error exc)
        {:status 500
         :body   {:type :draft/add-error :message "An unexpected error occurred."}}))))

(defmethod integrant.core/init-key ::draft-remove-unit
  [_init-key dependencies]
  (fn [request]
    (try
      (let [{{{:keys [eid unit-eid]} :path
              {:keys [section]}      :query} :parameters} request
            draft         (domain/get-draft-by-eid dependencies eid)
            game-mode     (domain/get-game-mode-by-eid dependencies (:game-mode-eid draft))
            state         (domain/get-draft-state dependencies eid)
            section-k     (keyword section)
            old-list      (get state section-k [])
            idx           (.indexOf old-list unit-eid)
            new-list      (if (>= idx 0)
                            (into [] (concat (subvec old-list 0 idx)
                                             (subvec old-list (inc idx))))
                            old-list)
            new-state     (assoc state section-k new-list)
            reinf-enabled (= 1 (:reinforcements-enabled game-mode))]
        (domain/set-draft-state dependencies eid new-state)
        (let [all-units   (hydrate-units-with-stats
                           (domain/get-units-for-faction dependencies (:faction-eid draft)))
              unit-by-eid (into {} (map (juxt :eid identity) all-units))
              hydrate     (fn [eids] (vec (keep unit-by-eid eids)))
              main-ctx    (build-section-context "main" (hydrate (:main new-state)) eid game-mode)
              reinf-ctx   (build-section-context "reinforcements" (hydrate (:reinforcements new-state)) eid game-mode)]
          {:status 200
           :body   {:type          :draft/remove-success
                    :main-section  (assoc main-ctx :oob true)
                    :reinf-section (when reinf-enabled (assoc reinf-ctx :oob true))}}))
      (catch Exception exc
        (log/error exc)
        {:status 200
         :body   {:type :draft/add-error :message "An unexpected error occurred."}}))))
