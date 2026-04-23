(ns com.devereux-henley.rts-web.web.league
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]
   [reitit.core]))

(defn get-league-by-eid
  [dependencies eid]
  (or (domain/get-league-by-eid dependencies eid)
      {:type :missing/resource :name "league" :id eid}))

(defn get-leagues-for-game
  [dependencies game-eid {:keys [hostname router]}]
  {:type      :collection/league
   :_embedded {:results (domain/get-leagues-for-game dependencies game-eid)}
   :_links    {:self (str hostname
                          (-> router
                              (reitit.core/match-by-name! :league/for-game)
                              (reitit.core/match->path {:game-eid game-eid})))}})

(defmethod integrant.core/init-key ::get-league
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        router                :reitit.core/router
        :as                   _request}]
    (web.core/handle-fetch-response
     domain/league-resource
     {:hostname (:hostname dependencies) :router router}
     #(get-league-by-eid dependencies eid))))

(defmethod integrant.core/init-key ::get-leagues
  [_init-key dependencies]
  (fn [{{{:keys [game-eid]} :query} :parameters
        router                      :reitit.core/router
        :as                         _request}]
    (web.core/handle-fetch-response
     domain/league-collection-resource
     {:hostname (:hostname dependencies) :router router}
     #(get-leagues-for-game dependencies game-eid
                            {:hostname (:hostname dependencies) :router router}))))

(defmethod integrant.core/init-key ::create-league
  [_init-key dependencies]
  (fn [{{{:keys [name description game-eid]} :body
         {:keys [version]}                   :query
         {:keys [eid]}                       :path} :parameters
        router                                      :reitit.core/router
        session                                     :ory-session
        :as                                         _request}]
    (let [response (web.core/handle-create-response
                    domain/league-resource
                    {:hostname (:hostname dependencies) :router router}
                    #(domain/create-league
                      dependencies
                      {:eid            eid
                       :game-eid       game-eid
                       :name           name
                       :description    description
                       :created-by-sub (get-in session [:identity :id])
                       :version        (or version 1)}))]
      (assoc-in response [:headers "HX-Redirect"]
                (str "/view/game/" game-eid "/league/" eid "/index.html")))))
