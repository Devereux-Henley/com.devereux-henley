(ns com.devereux-henley.rts-api.configuration
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [com.devereux-henley.rts-api.db :as db]
   [com.devereux-henley.rts-api.schema :as schema]
   [com.devereux-henley.rts-api.web :as web]
   [com.devereux-henley.rts-api.web.asset :as web.asset]
   [com.devereux-henley.rts-api.web.game :as web.game]
   [com.devereux-henley.rts-api.web.social-media :as web.social-media]
   [integrant.core]
   [clojure.java.io :as io]
   [selmer.parser]))

(def hostname (or (System/getenv "RTS_API_HOSTNAME") "http://localhost:3001"))
(def core-configuration
  {::db/connection       {}
   ::web/swagger-handler {}
   ::web.game/get-game   {:hostname hostname
                          :connection (integrant.core/ref ::db/connection)}
   ::web.game/get-games  {:hostname hostname
                          :connection (integrant.core/ref ::db/connection)}
   ::web.game/get-game-social-link {:hostname hostname
                                    :connection (integrant.core/ref ::db/connection)}
   ::web.social-media/get-platform {:hostname hostname
                                    :connection (integrant.core/ref ::db/connection)}
   ::web/routes
   [["/"
     {:get {:no-doc   true
            :produces ["text/html"]
            :handler  (fn [_request] {:status 301 :headers {"Location" "/dashboard.html"}})}}]
    ["/icon/social-media/:eid"
     {:get {:no-doc true
            :parameters {:path schema.contract/id-path-parameter}
            :produces ["image/svg+xml"]
            :handler web.asset/icon-handler}}]
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
              :swagger   {:tags         ["game"]
                          :produces     ["application/htmx+html" "application/json"]
                          :operation-id "get-games"}
              :responses {200 {:body schema/game-collection-resource}}
              :handler   (integrant.core/ref ::web.game/get-games)}}]

     ["/game/:eid"
      {:name :game/by-id
       :get  {:summary    "Fetches a game by eid."
              :swagger    {:tags         ["game"]
                           :produces     ["application/htmx+html" "application/json"]
                           :operation-id "get-game"}
              :parameters {:path  schema.contract/id-path-parameter
                           :query schema.contract/version-query-parameter}
              :responses  {200 {:body schema/game-resource}}
              :handler    (integrant.core/ref ::web.game/get-game)}}]
     ["/game/social-link/:eid"
      {:name :game/social-by-id
       :get  {:summary    "Fetches a link between social media and a game by eid."
              :swagger    {:tags         ["game"]
                           :produces     ["application/htmx+html" "application/json"]
                           :operation-id "get-game-social-link"}
              :parameters {:path  schema.contract/id-path-parameter
                           :query schema.contract/version-query-parameter}
              :responses  {200 {:body schema/game-social-link-resource}}
              :handler    (integrant.core/ref ::web.game/get-game-social-link)}}]
     ["/social-media/:eid"
      {:name :social-media/by-id
       :get  {:summary    "Fetches a social media platform by eid."
              :swagger    {:tags         ["social-media"]
                           :produces     ["application/htmx+html" "application/json"]
                           :operation-id "get-social-media-platform"}
              :parameters {:path  schema.contract/id-path-parameter
                           :query schema.contract/version-query-parameter}
              :responses  {200 {:body schema/social-media-platform-resource}}
              :handler    (integrant.core/ref ::web.social-media/get-platform)}}]]]
   ::web/app     {:routes (integrant.core/ref ::web/routes)}
   ::web/service {:handler       (integrant.core/ref ::web/app)
                  :configuration {:port 3001, :join? false}}
   })
