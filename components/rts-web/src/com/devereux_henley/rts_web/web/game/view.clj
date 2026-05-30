(ns com.devereux-henley.rts-web.web.game.view
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [integrant.core]))

(defmethod integrant.core/init-key ::faction-list-view
  [_init-key _dependencies]
  (partial web.view/standard-view-handler "faction-list.html"))

(defmethod integrant.core/init-key ::game-index-view
  [_init-key _dependencies]
  (fn [request]
    {:status 200
     :body   (render/render-view "game-index.html"
                                 (assoc (web.view/base-context request)
                                        :data (:game (:game-context request))))}))

(defn- units-by-category
  "Group a faction's units by category. The query sorts by
  `(unit-category-name, name)` so a sequential partition produces stable
  groups without resorting."
  [units]
  (mapv (fn [group]
          {:category (:unit-category-name (first group))
           :units    (vec group)})
        (partition-by :unit-category-name units)))

(defmethod integrant.core/init-key ::faction-view
  [_init-key {:keys [datalog-connection]}]
  (partial web.view/standard-entity-view-handler
           (fn [eid]
             (if-let [faction (db/faction-by-eid datalog-connection eid)]
               (assoc-in faction [:_embedded :units-by-category]
                         (units-by-category
                          (db/units-for-faction datalog-connection eid)))
               {:type :missing/resource :name "faction" :id eid}))
           "faction.html"
           (fn [_data _request] {})))

(defmethod integrant.core/init-key ::unit-view
  [_init-key dependencies]
  (partial web.view/standard-entity-view-handler
           (fn [eid] (domain/unit-view-model dependencies eid))
           "unit.html"
           ;; The model carries the unit fields (rendered under `data`); lift
           ;; the presentation lists to the top level the template reads, and
           ;; add the optional unit-card asset path.
           (fn [data _request]
             (let [portrait-stem (:eid data)]
               (assoc (select-keys data [:unit-statistics :abilities :draftable-spells :mounts :items])
                      :unit-card (when (io/resource
                                        (str "rts-web/asset/card/unit/" portrait-stem ".png"))
                                   (str "/card/unit/" portrait-stem ".png")))))))
