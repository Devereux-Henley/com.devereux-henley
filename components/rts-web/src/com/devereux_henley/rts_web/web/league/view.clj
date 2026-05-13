(ns com.devereux-henley.rts-web.web.league.view
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [integrant.core]))

(defmethod integrant.core/init-key ::create-league-view
  [_init-key _dependencies]
  (fn [request]
    {:status 200
     :body   (render/render "view/create-league.html"
                            (assoc (web.view/base-context request)
                                   :league-eid (random-uuid)))}))

(defmethod integrant.core/init-key ::league-view
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters :as request}]
    (let [league (domain/get-league-by-eid dependencies eid)]
      (if (nil? league)
        {:status 404 :body {:type :missing/resource :name "league" :id eid}}
        (let [seasons           (domain/get-seasons-for-league dependencies eid)
              all-tournaments   (domain/get-tournaments-for-game dependencies (:game-eid league))
              league-tourneys   (filter #(= eid (:league-eid %)) all-tournaments)
              eid->season       (into {} (map (juxt :eid identity) seasons))
              enriched-tourneys (mapv (fn [t]
                                        (let [state (domain/get-tournament-state dependencies (:eid t))]
                                          (cond-> (assoc t :status (:status state))
                                            (:season-eid t) (assoc :season-display-name (get-in eid->season [(:season-eid t) :display-name])))))
                                      league-tourneys)
              standings         (domain/get-league-faction-standings dependencies eid)
              user-sub          (get-in request [:ory-session :identity :id])]
          {:status 200
           :body   (render/render "view/league-index.html"
                                  (assoc (web.view/base-context request)
                                         :data league
                                         :seasons seasons
                                         :tournaments enriched-tourneys
                                         :standings standings
                                         :is-organizer (= user-sub (:created-by-sub league))))})))))
