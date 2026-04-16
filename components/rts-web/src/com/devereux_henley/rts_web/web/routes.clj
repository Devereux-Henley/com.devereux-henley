(ns com.devereux-henley.rts-web.web.routes
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.asset :as web.asset]
   [com.devereux-henley.rts-web.web.configuration :as web.configuration]
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

(def status-route
  ["/status"
   {:get {:no-doc  true
          :handler (fn [_request]
                     {:status 200
                      :headers {"Content-Type" "application/json"}
                      :body    "{\"status\":\"ok\"}"})}}])

(def shutdown-route
  ["/shutdown"
   {:post {:no-doc  true
           :handler (fn [_request]
                      (.start (Thread. (fn []
                                         (Thread/sleep 100)
                                         (System/exit 0))))
                      {:status 200
                       :headers {"Content-Type" "application/json"}
                       :body    "{\"status\":\"shutting-down\"}"})}}])

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
           :handler  (integrant.core/ref ::web.view/logout-view)}}]
   ["/game/:game-eid"
    {:middleware [(integrant.core/ref ::web.view/game-context-middleware)]}
    ["/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/game-id-path-parameter}
            :handler    (integrant.core/ref ::web.view/game-index-view)}}]
    ["/faction/:eid/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/game-and-id-path-parameter}
            :handler    (integrant.core/ref ::web.view/faction-view)}}]
    ["/unit/:eid/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/game-and-id-path-parameter}
            :handler    (integrant.core/ref ::web.view/unit-view)}}]
    ["/draft"
     ["/create.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/create-draft-view)}}]
     ["/me.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/my-drafts-view)}}]
     ["/:eid/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/draft-view)}}]]
    ["/tournament"
     ["/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/tournament-list-view)}}]
     ["/create.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/create-tournament-view)}}]
     ["/:eid/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/tournament-view)}}]]]])

(def api-routes
  ["/api"
   {:openapi {:security [{"ory" ["openid"]}]}}
   ["/openapi.json"
    {:get {:no-doc  true
           :openapi {:info {:title       "rts-api"
                            :description "Rts API"}
                     :components {:securitySchemes {"ory" {:type :openIdConnect
                                                           :openIdConnectUrl (integrant.core/ref ::web.configuration/openid-url)}}}
                     :tags [{:name "game" :description "game api"}]}
           :handler (integrant.core/ref :com.devereux-henley.rts-api.web/openapi-handler)}}]

   ["/game"
    {:name :collection/game
     :get  {:summary   "Fetches a list of games."
            :openapi   {:tags         ["game"]
                        :produces     ["application/htmx+html" "application/json"]
                        :operation-id "game/all"}
            :responses {200 {:body domain/game-collection-resource}}
            :handler   (integrant.core/ref ::web.game/get-games)}}]

   ["/game/:eid"
    {:name :game/by-eid
     :get  {:summary    "Fetches a game by eid."
            :openapi    {:tags         ["game"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "game/by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query web.game/game-query-parameters}
            :responses  {200 {:body domain/game-resource}}
            :handler    (integrant.core/ref ::web.game/get-game)}}]
   ["/game/social-link/:eid"
    {:name :game/social-by-eid
     :get  {:summary    "Fetches a link between social media and a game by eid."
            :openapi    {:tags         ["game"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "game/social-by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body domain/game-social-link-resource}}
            :handler    (integrant.core/ref ::web.game/get-game-social-link)}}]
   ["/game/faction/:eid"
    {:name :game/faction-by-eid
     :get  {:summary    "Fetches a game faction by eid."
            :openapi    {:tags         ["game"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "game/faction-by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query web.game/faction-query-parameters}
            :responses  {200 {:body domain/faction-resource}}
            :handler    (integrant.core/ref ::web.game/get-faction)}}]
   ["/draft/:draft-eid/unit/:eid"
    {:name :draft-unit/by-eid
     :get  {:produces   ["application/json" "application/htmx+html"]
            :openapi    {:summary      "Gets details for a unit that can be assigned to the specific draft."
                         :tags         ["draft"]
                         :produces     ["application/json" "application/htmx+html"]
                         :operation-id "draft-unit/get"}
            :parameters {:path  (schema.contract/to-schema
                                 [:map
                                  [:draft-eid :uuid]
                                  [:eid :uuid]])}
            :responses  {200 {:body domain/draft-unit-resource}
                         500 {:body domain/draft-error-response}}
            :handler    (integrant.core/ref ::web.draft/get-draft-unit)}
     :post {:produces   ["application/json" "application/htmx+html"]
            :openapi    {:summary      "Assigns a unit to the specified draft."
                         :tags         ["draft"]
                         :produces     ["application/json" "application/htmx+html"]
                         :operation-id "draft-unit/create"}
            :parameters {:path  (schema.contract/to-schema
                                 [:map
                                  [:draft-eid :uuid]
                                  [:eid :uuid]])
                         :query (schema.contract/to-schema
                                 [:map
                                  [:section [:enum "main" "reinforcements"]]])
                         :body  domain/add-unit-to-draft-specification}
            :responses  {200 {:body domain/draft-add-response}
                         422 {:body domain/draft-error-response}
                         500 {:body domain/draft-error-response}}
            :handler    (integrant.core/ref ::web.draft/draft-add-unit)}}]
   ["/draft/:draft-eid/entry/:eid"
    {:name :draft-entry/by-eid
     :get    {:produces   ["application/json" "application/htmx+html"]
              :openapi    {:summary      "Gets a placed draft entry with its unit details and selection state."
                           :tags         ["draft"]
                           :produces     ["application/json" "application/htmx+html"]
                           :operation-id "draft-entry/get"}
              :parameters {:path  (schema.contract/to-schema
                                   [:map
                                    [:draft-eid :uuid]
                                    [:eid :uuid]])
                           :query (schema.contract/to-schema
                                   [:map
                                    [:section [:enum "main" "reinforcements"]]
                                    [:embed   {:optional true} [:or [:enum "unit"]
                                                                [:sequential [:enum "unit"]]]]])}
              :responses  {200 {:body domain/draft-entry-resource}
                           404 {:body domain/draft-error-response}
                           500 {:body domain/draft-error-response}}
              :handler    (integrant.core/ref ::web.draft/get-draft-entry)}
     :patch  {:produces   ["application/json" "application/htmx+html"]
              :openapi    {:summary      "Updates the selections of a placed draft entry."
                           :tags         ["draft"]
                           :produces     ["application/json" "application/htmx+html"]
                           :operation-id "draft-entry/update"}
              :parameters {:path  (schema.contract/to-schema
                                   [:map
                                    [:draft-eid :uuid]
                                    [:eid :uuid]])
                           :query (schema.contract/to-schema
                                   [:map
                                    [:section [:enum "main" "reinforcements"]]])
                           :body  domain/add-unit-to-draft-specification}
              :responses  {200 {:body domain/draft-update-response}
                           422 {:body domain/draft-error-response}
                           500 {:body domain/draft-error-response}}
              :handler    (integrant.core/ref ::web.draft/draft-update-unit)}
     :delete {:produces   ["application/json" "application/htmx+html"]
              :openapi    {:summary      "Removes a placed entry from the specified draft."
                           :tags         ["draft"]
                           :produces     ["application/json" "application/htmx+html"]
                           :operation-id "draft-entry/delete"}
              :parameters {:path  (schema.contract/to-schema
                                   [:map
                                    [:draft-eid :uuid]
                                    [:eid :uuid]])
                           :query (schema.contract/to-schema
                                   [:map
                                    [:section [:enum "main" "reinforcements"]]])}
              :responses  {200 {:body domain/draft-remove-response}
                           500 {:body domain/draft-error-response}}
              :handler    (integrant.core/ref ::web.draft/draft-remove-unit)}}]
   ["/draft/:eid"
    {:name :draft/by-eid
     :put  {:summary    "Creates a draft with the given eid and version."
            :openapi    {:tags         ["draft"]
                         :produces     ["application/json" "application/htmx+html"]
                         :operation-id "draft/create"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter
                         :body  domain/create-draft-specification}
            :responses  {201 {:body domain/draft-resource}}
            :handler    (integrant.core/ref ::web.draft/create-draft)}}]

   ["/tournament"
    [""
     {:name :tournament/for-game
      :get  {:summary    "Fetches tournaments for a game."
             :openapi    {:tags         ["tournament"]
                          :produces     ["application/json"]
                          :operation-id "tournament/for-game"}
             :parameters {:query (schema.contract/to-schema
                                  [:map
                                   [:game-eid :uuid]])}
             :responses  {200 {:body domain/tournament-collection-resource}}
             :handler    (integrant.core/ref ::web.tournament/get-tournaments)}}]
    ["/:eid"
     [""
      {:name :tournament/by-eid
       :get  {:summary    "Fetches a tournament by eid."
              :openapi    {:tags         ["tournament"]
                           :produces     ["application/json"]
                           :operation-id "tournament/by-eid"}
              :parameters {:path schema.contract/id-path-parameter}
              :responses  {200 {:body domain/tournament-resource}}
              :handler    (integrant.core/ref ::web.tournament/get-tournament)}
       :put  {:summary    "Creates a tournament with the given eid."
              :openapi    {:tags         ["tournament"]
                           :produces     ["application/json"]
                           :operation-id "tournament/create"}
              :parameters {:path  schema.contract/id-path-parameter
                           :query schema.contract/version-query-parameter
                           :body  domain/create-tournament-specification}
              :responses  {201 {:body domain/tournament-resource}}
              :handler    (integrant.core/ref ::web.tournament/create-tournament)}}]
     ["/register"
      {:post   {:summary    "Register the current player for a tournament."
                :openapi    {:tags         ["tournament"]
                             :produces     ["application/json"]
                             :operation-id "tournament/register"}
                :parameters {:path schema.contract/id-path-parameter}
                :handler    (integrant.core/ref ::web.tournament/register-player)}
       :delete {:summary    "Withdraw the current player from a tournament."
                :openapi    {:tags         ["tournament"]
                             :produces     ["application/json"]
                             :operation-id "tournament/withdraw"}
                :parameters {:path schema.contract/id-path-parameter}
                :handler    (integrant.core/ref ::web.tournament/withdraw-player)}}]
     ["/registrations"
      {:get {:summary    "List active registrations for a tournament."
             :openapi    {:tags         ["tournament"]
                          :produces     ["application/json"]
                          :operation-id "tournament/registrations"}
             :parameters {:path schema.contract/id-path-parameter}
             :handler    (integrant.core/ref ::web.tournament/get-registrations)}}]]]

   ["/social-media/:eid"
    {:name :social-media/by-eid
     :get  {:summary    "Fetches a social media platform by eid."
            :openapi    {:tags         ["social-media"]
                         :produces     ["application/htmx+html" "application/json"]
                         :operation-id "social-media/by-eid"}
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body domain/social-media-platform-resource}}
            :handler    (integrant.core/ref ::web.social-media/get-platform)}}]])

(defmethod integrant.core/init-key ::routes
  [_init-key routes]
  routes)
