(ns com.devereux-henley.rts-web.web.draft
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]
   [selmer.parser]
   [taoensso.timbre :as log]))

(defn section-pct [cost max-val]
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
        pct           (section-pct cost section-max)
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
     :section-pct         pct
     :section-near-limit  (> pct 85)
     :section-over-budget (> cost section-max)
     :draft-eid           draft-eid}))

(defn ^:private render-army-section
  [context]
  {:status  200
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (selmer.parser/render-file "rts-api/view/draft-army-section.html" context)})

(defn ^:private error-fragment
  [status message]
  {:status  status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (selmer.parser/render-file
             "rts-api/view/draft-error-fragment.html"
             {:message message})})

(defmethod integrant.core/init-key ::draft-add-unit
  [_init-key dependencies]
  (fn [request]
    (try
      (let [{{{:keys [eid]} :path
              {:keys [unit-eid section]} :body} :parameters} request
            draft         (domain/get-draft-by-eid dependencies eid)
            game-mode     (domain/get-game-mode-by-eid dependencies (:game-mode-eid draft))
            unit          (domain/get-unit-by-eid dependencies unit-eid)
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
            lords-in      (count (filter :is-lord units-in))]
        (cond
          (> total-cost section-max)
          (error-fragment 422 (str "Adding " (:name unit) " would exceed the " section " army budget of " section-max "."))

          (and is-lord (> lords-in 0))
          (error-fragment 422 "Only one lord may be added to an army section.")

          :else
          (let [new-state (update state section-k (fnil conj []) unit-eid)
                new-units (hydrate (get new-state section-k []))]
            (domain/set-draft-state dependencies eid new-state)
            (render-army-section (build-section-context section new-units eid game-mode)))))
      (catch Exception exc
        (log/error exc)
        (error-fragment 500 "An unexpected error occurred.")))))

(defmethod integrant.core/init-key ::draft-remove-unit
  [_init-key dependencies]
  (fn [request]
    (try
      (let [{{{:keys [eid unit-eid]} :path
              {:keys [section]}      :query} :parameters} request
            draft       (domain/get-draft-by-eid dependencies eid)
            game-mode   (domain/get-game-mode-by-eid dependencies (:game-mode-eid draft))
            state       (domain/get-draft-state dependencies eid)
            section-k   (keyword section)
            old-list    (get state section-k [])
            idx         (.indexOf old-list unit-eid)
            new-list    (if (>= idx 0)
                          (into [] (concat (subvec old-list 0 idx) (subvec old-list (inc idx))))
                          old-list)
            new-state   (assoc state section-k new-list)]
        (domain/set-draft-state dependencies eid new-state)
        (let [all-units   (hydrate-units-with-stats
                           (domain/get-units-for-faction dependencies (:faction-eid draft)))
              unit-by-eid (into {} (map (juxt :eid identity) all-units))
              hydrate     (fn [eids] (vec (keep unit-by-eid eids)))
              new-units   (hydrate new-list)]
          (render-army-section (build-section-context section new-units eid game-mode))))
      (catch Exception exc
        (log/error exc)
        (error-fragment 500 "An unexpected error occurred.")))))
