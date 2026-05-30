(ns com.devereux-henley.rts-web.web.game.view
  (:require
   [clojure.java.io :as io]
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

(defmethod integrant.core/init-key ::faction-view
  [_init-key dependencies]
  (fn [request]
    (web.view/render-entity-view request "faction.html"
                                 (domain/faction-view-model dependencies (-> request :parameters :path :eid)))))

(defn- unit-card-path
  "The web-served path to a unit's card image when the asset exists on the
  classpath, else nil. Filesystem lookup is a view-only concern, so it stays
  in the web layer rather than the domain view-model."
  [unit-eid]
  (when (io/resource (str "rts-web/asset/card/unit/" unit-eid ".png"))
    (str "/card/unit/" unit-eid ".png")))

(defmethod integrant.core/init-key ::unit-view
  [_init-key dependencies]
  (fn [request]
    (let [view-model (domain/unit-view-model dependencies (-> request :parameters :path :eid))]
      (web.view/render-entity-view request "unit.html"
                                   (cond-> view-model
                                     (not= :missing/resource (:type view-model))
                                     (assoc :unit-card (unit-card-path (:eid (:data view-model)))))))))
