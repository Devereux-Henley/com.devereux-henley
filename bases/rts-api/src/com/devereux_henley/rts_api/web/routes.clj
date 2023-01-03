(ns com.devereux-henley.rts-api.web.routes
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [com.devereux-henley.rts-api.schema :as schema]
   [com.devereux-henley.rts-api.web :as web]
   [com.devereux-henley.rts-api.web.asset :as web.asset]
   [com.devereux-henley.rts-api.web.game :as web.game]
   [com.devereux-henley.rts-api.web.social-media :as web.social-media]
   [com.devereux-henley.rts-api.web.view :as web.view]
   [integrant.core]))

(def root-route
  ["/"
   {:get {:no-doc   true
          :produces ["text/html"]
          :handler  (fn [_request] {:status 301 :headers {"Location" "/view/dashboard.html"}})}}])

(def icon-routes
  ["/icon/social-media/:eid"
   {:get {:no-doc     true
          :parameters {:path schema.contract/id-path-parameter}
          :produces   ["image/svg+xml"]
          :handler    web.asset/icon-handler}}])

(def view-routes
  ["/view"
   {:no-doc true}
   ["/dashboard.html"
    {:get {:produces ["text/html"]
           :handler  (integrant.core/ref ::web.view/dashboard-view)}}]
   ["/game.html"
    {:get {:produces ["text/html"]
           :handler  (integrant.core/ref ::web.view/game-view)}}]
   ["/contact.html"
    {:get {:produces ["text/html"]
           :handler  (integrant.core/ref ::web.view/contact-view)}}]
   ["/about.html"
    {:get {:produces ["text/html"]
           :handler  (integrant.core/ref ::web.view/about-view)}}]
   ["/login.html"
    {:get {:produces ["text/html"]
           :handler  (integrant.core/ref ::web.view/login-view)}}]
   ["/logout.html"
    {:get {:produces ["text/html"]
           :handler  (integrant.core/ref ::web.view/logout-view)}}]])

(def api-routes
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
                        :operation-id "game/all"}
            :responses {200 {:body schema/game-collection-resource}}
            :handler   (integrant.core/ref ::web.game/get-games)}}]

   ["/game/:eid"
    {:name :game/by-eid
     :get  {:summary    "Fetches a game by eid."
            :swagger    {:tags         ["game"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "game/by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body schema/game-resource}}
            :handler    (integrant.core/ref ::web.game/get-game)}}]
   ["/game/social-link/:eid"
    {:name :game/social-by-eid
     :get  {:summary    "Fetches a link between social media and a game by eid."
            :swagger    {:tags         ["game"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "game/social-by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body schema/game-social-link-resource}}
            :handler    (integrant.core/ref ::web.game/get-game-social-link)}}]
   ["/game/faction/:eid"
    {:name :game/faction-by-eid
     :get  {:summary    "Fetches a game faction by eid."
            :swagger    {:tags         ["game"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "game/faction-by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body schema/faction-resource}}
            :handler    (integrant.core/ref ::web.game/get-faction)}}]
   ["/social-media/:eid"
    {:name :social-media/by-eid
     :get  {:summary    "Fetches a social media platform by eid."
            :swagger    {:tags         ["social-media"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "social-media/by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body schema/social-media-platform-resource}}
            :handler    (integrant.core/ref ::web.social-media/get-platform)}}]])

(defmethod integrant.core/init-key ::routes
  [_init-key routes]
  routes)
