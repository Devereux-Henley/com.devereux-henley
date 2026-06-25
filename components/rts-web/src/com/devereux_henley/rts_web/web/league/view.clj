(ns com.devereux-henley.rts-web.web.league.view
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [integrant.core]))

(defmethod integrant.core/init-key ::leagues-view
  [_init-key dependencies]
  (fn [request]
    (let [game-eid         (:game-eid (:game-context request))
          tournaments      (domain/get-tournaments-for-game dependencies game-eid)
          leagues          (domain/get-leagues-for-game dependencies game-eid)
          enriched-leagues (mapv (fn [l]
                                   (let [current-season (domain/get-current-season-for-league dependencies (:eid l))
                                         tcount         (count (filter #(= (:eid l) (:league-eid %)) tournaments))]
                                     (assoc l
                                            :current-season current-season
                                            :tournament-count tcount)))
                                 leagues)]
      {:status 200
       :body   (render/render-view "leagues.html"
                                   (assoc (web.view/base-context request)
                                          :leagues enriched-leagues))})))

(defmethod integrant.core/init-key ::create-league-view
  [_init-key _dependencies]
  (fn [request]
    {:status 200
     :body   (render/render-view "create-league.html"
                                 (assoc (web.view/base-context request)
                                        :league-eid (random-uuid)))}))

(defmethod integrant.core/init-key ::league-view
  [_init-key dependencies]
  (fn [request]
    (web.view/render-entity-view request "league-index.html"
                                 (domain/league-view-model dependencies
                                                           (-> request :parameters :path :eid)
                                                           (-> request :ory-session :identity :id)))))
