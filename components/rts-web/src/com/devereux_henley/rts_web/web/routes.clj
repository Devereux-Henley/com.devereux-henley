(ns com.devereux-henley.rts-web.web.routes
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.asset :as web.asset]
   [com.devereux-henley.rts-web.web.draft :as web.draft]
   [com.devereux-henley.rts-web.web.game :as web.game]
   [com.devereux-henley.rts-web.web.social-media :as web.social-media]
   [com.devereux-henley.rts-web.web.tournament :as web.tournament]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [com.devereux-henley.schema.contract :as schema.contract]
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
   ["/tournament.html"
    {:get {:produces ["text/html"]
           :handler  (integrant.core/ref ::web.view/tournament-view)}}]
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
           :handler  (integrant.core/ref ::web.view/logout-view)}}]
   ["/game/:game-eid"
    {:middleware [(integrant.core/ref ::web.view/game-context-middleware)]}
    ["/index.html"
     {:get {:produces   ["text/html"]
            :parameters {:path schema.contract/game-id-path-parameter}
            :handler    (integrant.core/ref ::web.view/game-index-view)}}]
    ["/faction/:eid/index.html"
     {:get {:produces   ["text/html"]
            :parameters {:path schema.contract/game-and-id-path-parameter}
            :handler    (integrant.core/ref ::web.view/faction-view)}}]
    ["/unit/:eid/index.html"
     {:get {:produces   ["text/html"]
            :parameters {:path schema.contract/game-and-id-path-parameter}
            :handler    (integrant.core/ref ::web.view/unit-view)}}]
    ["/draft"
     ["/create.html"
      {:get {:produces   ["text/html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/create-draft-view)}}]
     ["/me.html"
      {:get {:produces   ["text/html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/my-drafts-view)}}]
     ["/:eid/index.html"
      {:get {:produces   ["text/html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/draft-view)}}]]]])

(def api-routes
  ["/api"
   ["/swagger.json"
    {:get {:no-doc  true
           :swagger {:info {:title       "rts-api"
                            :description "Rts API"}
                     :tags [{:name "game" :description "game api"}]}
           :handler (integrant.core/ref :com.devereux-henley.rts-api.web/swagger-handler)}}]

   ["/game"
    {:name :collection/game
     :get  {:summary   "Fetches a list of games."
            :swagger   {:tags         ["game"]
                        :produces     ["application/htmx+html" "application/json"]
                        :operation-id "game/all"}
            :responses {200 {:body domain/game-collection-resource}}
            :handler   (integrant.core/ref ::web.game/get-games)}}]

   ["/game/:eid"
    {:name :game/by-eid
     :get  {:summary    "Fetches a game by eid."
            :swagger    {:tags         ["game"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "game/by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query web.game/game-query-parameters}
            :responses  {200 {:body domain/game-resource}}
            :handler    (integrant.core/ref ::web.game/get-game)}}]
   ["/game/social-link/:eid"
    {:name :game/social-by-eid
     :get  {:summary    "Fetches a link between social media and a game by eid."
            :swagger    {:tags         ["game"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "game/social-by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body domain/game-social-link-resource}}
            :handler    (integrant.core/ref ::web.game/get-game-social-link)}}]
   ["/game/faction/:eid"
    {:name :game/faction-by-eid
     :get  {:summary    "Fetches a game faction by eid."
            :swagger    {:tags         ["game"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "game/faction-by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query web.game/faction-query-parameters}
            :responses  {200 {:body domain/faction-resource}}
            :handler    (integrant.core/ref ::web.game/get-faction)}}]
   ["/draft/:eid/unit/:unit-eid"
    {:post   {:no-doc     true
              :produces   ["text/html"]
              :parameters {:path  (schema.contract/to-schema
                                   [:map
                                    [:eid :uuid]
                                    [:unit-eid :uuid]])
                           :query (schema.contract/to-schema
                                   [:map
                                    [:section [:enum "main" "reinforcements"]]])}
              :handler    (integrant.core/ref ::web.draft/draft-add-unit)}
     :delete {:no-doc     true
              :produces   ["text/html"]
              :parameters {:path  (schema.contract/to-schema
                                   [:map
                                    [:eid :uuid]
                                    [:unit-eid :uuid]])
                           :query (schema.contract/to-schema
                                   [:map
                                    [:section [:enum "main" "reinforcements"]]])}
              :handler    (integrant.core/ref ::web.draft/draft-remove-unit)}}]
   ["/draft/:eid/unit/:unit-eid/panel"
    {:get {:no-doc     true
           :produces   ["text/html"]
           :parameters {:path (schema.contract/to-schema
                               [:map
                                [:eid :uuid]
                                [:unit-eid :uuid]])}
           :handler    (integrant.core/ref ::web.draft/draft-unit-panel)}}]
   ["/draft/:eid"
    {:name :draft/by-eid
     :put  {:summary    "Creates a draft with the given eid and version."
            :swagger    {:tags         ["draft"]
                         :produces     ["application/json"]
                         :operation-id "draft/create"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter
                         :body  domain/create-draft-specification}
            :responses  {201 {:body domain/draft-resource}}
            :handler    (integrant.core/ref ::web.game/create-draft)}}]

   ["/tournament"
    {:name :collection/tournament
     :get  {:summary    "A collection of all tournaments."
            :swagger    {:tags         ["collection" "tournament"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "collection/tournament"}
            :parameters {:query schema.contract/collection-parameters}
            :responses  {200 {:body domain/tournament-collection-resource}}
            :handler    (integrant.core/ref ::web.tournament/get-tournaments)}}]
   ["/tournament/:eid"
    {:name :tournament/by-eid
     :get  {:summary    "Fetches a tournament by eid."
            :swagger    {:tags         ["tournament"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "tournament/by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query web.tournament/tournament-query-parameters}
            :responses  {200 {:body domain/tournament-resource}}
            :handler    (integrant.core/ref ::web.tournament/get-tournament)}
     :put  {:summary    "Creates a tournament with the given eid and version."
            :swagger    {:tags         ["tournament"]
                         :produces     ["application/htmlx+html" "application/json"]
                         :operation-id "tournament/create"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter
                         :body  domain/create-tournament-specification}
            :responses  {201 {:body domain/tournament-resource}}
            :handler    (integrant.core/ref ::web.tournament/create-tournament)}}]
   ["/tournament/snapshot/:eid"
    {:name :tournament/snapshot-by-eid
     :get  {:summary    "Fetches a tournament snapshot by eid."
            :swagger    {:tags         ["tournament"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "tournament/snapshot-by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body domain/tournament-snapshot-resource}}
            :handler    (integrant.core/ref ::web.tournament/get-tournament-snapshot)}}]
   ["/social-media/:eid"
    {:name :social-media/by-eid
     :get  {:summary    "Fetches a social media platform by eid."
            :swagger    {:tags         ["social-media"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "social-media/by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body domain/social-media-platform-resource}}
            :handler    (integrant.core/ref ::web.social-media/get-platform)}}]])

(defmethod integrant.core/init-key ::routes
  [_init-key routes]
  routes)
