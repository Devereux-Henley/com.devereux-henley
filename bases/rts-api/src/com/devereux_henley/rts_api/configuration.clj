(ns com.devereux-henley.rts-api.configuration
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [com.devereux-henley.rts-api.db :as db]
   [com.devereux-henley.rts-api.schema :as schema]
   [com.devereux-henley.rts-api.web :as web]
   [com.devereux-henley.rts-api.web.game :as web.game]
   [integrant.core]
   [clojure.java.io :as io]
   [selmer.parser]))

(def hostname (or (System/getenv "RTS_API_HOSTNAME") "http://localhost:3001"))
(def core-configuration
  {::db/connection       {}
   ::web/swagger-handler {}
   ::web.game/get-game   {:connection (integrant.core/ref ::db/connection)}
   ::web.game/get-games  {:connection (integrant.core/ref ::db/connection)}
   ::web/routes
   [["/"
     {:get {:no-doc   true
            :produces ["text/html"]
            :handler  (fn [_request] {:status 301 :headers {"Location" "/dashboard.html"}})}}]
    ["/dashboard.html"
     {:get {:no-doc   true
            :produces ["text/html"]
            :handler  (fn [_request] {:status 200 :body (slurp (io/resource "rts-api/asset/dashboard.html"))})}}]
    ["/dashboard.css"
     {:get {:no-doc   true
            :produces ["application/css"]
            :handler  (fn [_request] {:status 200 :body (slurp (io/resource "rts-api/asset/dashboard.css"))})}}]
    ["/api"
     ["/swagger.json"
      {:get {:no-doc  true
             :swagger {:info {:title       "rts-api"
                              :description "Rts API"}
                       :tags [{:name "games" :description "game api"}]}
             :handler (integrant.core/ref ::web/swagger-handler)}}]

     ["/game"
      {:name :collection/game
       :get  {:summary   "Fetches a list of games."
              :swagger   {:tags         ["games"]
                          :produces     ["application/htmx+html" "application/json"]
                          :operation-id "get-games"}
              :responses {200 {:body schema/game-collection-resource}}
              :handler   (integrant.core/ref ::web.game/get-games)}}]

     ["/game/:eid"
      {:name :game/by-id
       :get  {:summary    "Fetches a game by id."
              :swagger    {:tags         ["games"]
                           :produces     ["application/htmx+html" "application/json"]
                           :operation-id "get-game"}
              :parameters {:path  schema.contract/id-path-parameter
                           :query schema.contract/version-query-parameter}
              :responses  {200 {:body schema/game-resource}}
              :handler    (integrant.core/ref ::web.game/get-game)}}]]]
   ::web/app     {:routes (integrant.core/ref ::web/routes)}
   ::web/service {:handler       (integrant.core/ref ::web/app)
                  :configuration {:port 3001, :join? false}}
   })
