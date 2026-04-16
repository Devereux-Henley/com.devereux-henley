(ns com.devereux-henley.rts-web.web.tournament
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]
   [reitit.core]))

(defn get-tournament-by-eid
  [dependencies eid]
  (or (domain/get-tournament-by-eid dependencies eid)
      {:type :missing/resource :name "tournament" :id eid}))

(defn get-tournaments-for-game
  [dependencies game-eid {:keys [hostname router]}]
  {:type      :collection/tournament
   :_embedded {:results (domain/get-tournaments-for-game dependencies game-eid)}
   :_links    {:self (str hostname
                          (-> router
                              (reitit.core/match-by-name! :tournament/for-game)
                              (reitit.core/match->path {:game-eid game-eid})))}})

(defmethod integrant.core/init-key ::get-tournament
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        router                :reitit.core/router
        :as                   _request}]
    (web.core/handle-fetch-response
     domain/tournament-resource
     {:hostname (:hostname dependencies) :router router}
     #(get-tournament-by-eid dependencies eid))))

(defmethod integrant.core/init-key ::get-tournaments
  [_init-key dependencies]
  (fn [{{{:keys [game-eid]} :query} :parameters
        router                      :reitit.core/router
        :as                         _request}]
    (web.core/handle-fetch-response
     domain/tournament-collection-resource
     {:hostname (:hostname dependencies) :router router}
     #(get-tournaments-for-game dependencies game-eid
                                {:hostname (:hostname dependencies) :router router}))))

(defmethod integrant.core/init-key ::create-tournament
  [_init-key dependencies]
  (fn [{{{:keys [name description game-eid timezone
                 registration-opens-at registration-closes-at]} :body
         {:keys [version]}                                      :query
         {:keys [eid]}                                          :path} :parameters
        router                                                          :reitit.core/router
        session                                                         :ory-session
        :as                                                             _request}]
    (let [response (web.core/handle-create-response
                    domain/tournament-resource
                    {:hostname (:hostname dependencies) :router router}
                    #(domain/create-tournament
                      dependencies
                      {:eid                    eid
                       :game-eid               game-eid
                       :name                   name
                       :description            description
                       :timezone               timezone
                       :registration-opens-at  registration-opens-at
                       :registration-closes-at registration-closes-at
                       :created-by-sub         (get-in session [:identity :id])
                       :version                version}))]
      (assoc-in response [:headers "HX-Redirect"]
                (str "/view/game/" game-eid "/tournament/" eid "/index.html")))))
