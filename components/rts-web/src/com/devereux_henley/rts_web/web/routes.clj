(ns com.devereux-henley.rts-web.web.routes
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.asset :as web.asset]
   [com.devereux-henley.rts-web.web.configuration :as web.configuration]
   [com.devereux-henley.rts-web.web.draft :as web.draft]
   [com.devereux-henley.rts-web.web.game :as web.game]
   [com.devereux-henley.rts-web.web.league :as web.league]
   [com.devereux-henley.rts-web.web.season :as web.season]
   [com.devereux-henley.rts-web.web.social-media :as web.social-media]
   [com.devereux-henley.rts-web.web.stats :as web.stats]
   [com.devereux-henley.rts-web.web.tournament :as web.tournament]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [com.devereux-henley.schema.contract :as schema.contract]
   [integrant.core]))

(def root-route
  ["/"
   {:get {:no-doc   true
          :produces ["text/html"]
          :handler  (fn [_request] {:status 301 :headers {"Location" "/view/game/index.html"}})}}])

(def status-route
  ["/status"
   {:get {:no-doc  true
          :handler (fn [_request]
                     {:status  200
                      :headers {"Content-Type" "application/json"}
                      :body    "{\"status\":\"ok\"}"})}}])

(def shutdown-route
  ["/shutdown"
   {:post {:no-doc  true
           :handler (fn [_request]
                      (.start (Thread. (fn []
                                         (Thread/sleep 100)
                                         (System/exit 0))))
                      {:status  200
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
   ["/game/index.html"
    {:get {:produces ["text/html"]
           :handler  (integrant.core/ref ::web.view/game-selector-view)}}]
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
    ["/faction/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/game-id-path-parameter}
            :handler    (integrant.core/ref ::web.view/faction-list-view)}}]
    ["/faction/:eid/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/game-and-id-path-parameter}
            :handler    (integrant.core/ref ::web.view/faction-view)}}]
    ["/unit/:eid/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path  schema.contract/game-and-id-path-parameter
                         :query (schema.contract/to-schema
                                 [:map
                                  [:lore {:optional true} [:maybe :string]]])}
            :handler    (integrant.core/ref ::web.view/unit-view)}}]
    ["/draft"
     ["/create.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/create-draft-view)}}]
     ["/me.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path  schema.contract/game-id-path-parameter
                          :query (schema.contract/to-schema
                                  [:map
                                   [:faction {:optional true} :string]])}
             :handler    (integrant.core/ref ::web.view/my-drafts-view)}}]
     ["/:eid/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/draft-view)}}]]
    ["/tournament"
     ["/create.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/create-tournament-view)}}]
     ["/:eid/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/tournament-view)}}]
     ["/:eid/phase.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/tournament-phase-form-view)}}]]
    ["/competitive"
     ["/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/competitive-view)}}]
     ["/season-options.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path  schema.contract/game-id-path-parameter
                          :query (schema.contract/to-schema
                                  [:map
                                   [:league-eid {:optional true} [:maybe :uuid]]])}
             :handler    (integrant.core/ref ::web.view/season-options-fragment-view)}}]]
    ["/league"
     ["/create.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/create-league-view)}}]
     ["/:eid/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.view/league-view)}}]
     ["/:league-eid/season"
      ["/create.html"
       {:get {:produces   ["application/htmx+html"]
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:game-eid :uuid]
                                   [:league-eid :uuid]])}
              :handler    (integrant.core/ref ::web.view/create-season-view)}}]
      ["/:eid/index.html"
       {:get {:produces   ["application/htmx+html"]
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:game-eid :uuid]
                                   [:league-eid :uuid]
                                   [:eid :uuid]])}
              :handler    (integrant.core/ref ::web.view/season-view)}}]]]]])

(def api-routes
  ["/api"
   {:openapi {:security [{"ory" ["openid"]}]}}
   ["/openapi.json"
    {:get {:no-doc  true
           :openapi {:info       {:title       "rts-api"
                                  :description "Rts API"}
                     :components {:securitySchemes {"ory" {:type             :openIdConnect
                                                           :openIdConnectUrl (integrant.core/ref ::web.configuration/openid-url)}}}
                     :tags       [{:name "game" :description "game api"}]}
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
                                  [:eid :uuid]])
                         :query (schema.contract/to-schema
                                 [:map
                                  [:mount     {:optional true} [:maybe :string]]
                                  [:lore      {:optional true} [:maybe :string]]
                                  [:items     {:optional true} [:or :string [:sequential :string]]]
                                  [:spells    {:optional true} [:or :string [:sequential :string]]]
                                  [:abilities {:optional true} [:or :string [:sequential :string]]]])}
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
    {:name   :draft-entry/by-eid
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
                                    [:section   [:enum "main" "reinforcements"]]
                                    [:embed     {:optional true} [:or [:enum "unit"]
                                                                  [:sequential [:enum "unit"]]]]
                                    [:mount     {:optional true} [:maybe :string]]
                                    [:lore      {:optional true} [:maybe :string]]
                                    [:items     {:optional true} [:or :string [:sequential :string]]]
                                    [:spells    {:optional true} [:or :string [:sequential :string]]]
                                    [:abilities {:optional true} [:or :string [:sequential :string]]]])}
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
    {:name  :draft/by-eid
     :put   {:summary    "Creates a draft with the given eid and version."
             :openapi    {:tags         ["draft"]
                          :produces     ["application/json" "application/htmx+html"]
                          :operation-id "draft/create"}
             :parameters {:path  schema.contract/id-path-parameter
                          :query schema.contract/version-query-parameter
                          :body  domain/create-draft-specification}
             :responses  {201 {:body domain/draft-resource}}
             :handler    (integrant.core/ref ::web.draft/create-draft)}
     :patch {:summary    "Applies a partial update to a draft (currently only :name)."
             :openapi    {:tags         ["draft"]
                          :produces     ["application/json" "application/htmx+html"]
                          :operation-id "draft/update"}
             :parameters {:path schema.contract/id-path-parameter
                          :body domain/update-draft-specification}
             :responses  {200 {:body domain/draft-resource}
                          500 {:body domain/draft-error-response}}
             :handler    (integrant.core/ref ::web.draft/update-draft)}}]

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
     ["/entry"
      ["/me"
       {:post   {:summary    "Create a tournament entry for the current player."
                 :openapi    {:tags         ["tournament"]
                              :produces     ["application/json"]
                              :operation-id "tournament-entry/create-mine"}
                 :parameters {:path schema.contract/id-path-parameter}
                 :handler    (integrant.core/ref ::web.tournament/create-entry)}
        :delete {:summary    "Remove the current player's tournament entry."
                 :openapi    {:tags         ["tournament"]
                              :produces     ["application/json"]
                              :operation-id "tournament-entry/delete-mine"}
                 :parameters {:path schema.contract/id-path-parameter}
                 :responses  {200 {:body domain/tournament-entry-deleted-response}}
                 :handler    (integrant.core/ref ::web.tournament/delete-entry)}}]
      [""
       {:get {:summary    "List active entries for a tournament."
              :openapi    {:tags         ["tournament"]
                           :produces     ["application/json"]
                           :operation-id "tournament-entry/list"}
              :parameters {:path schema.contract/id-path-parameter}
              :responses  {200 {:body domain/tournament-entries-response}}
              :handler    (integrant.core/ref ::web.tournament/get-entries)}}]]
     ["/status"
      {:get {:summary    "Get the current tournament status and available transitions."
             :openapi    {:tags         ["tournament"]
                          :produces     ["application/json"]
                          :operation-id "tournament-status/get"}
             :parameters {:path schema.contract/id-path-parameter}
             :responses  {200 {:body domain/tournament-status-response}}
             :handler    (integrant.core/ref ::web.tournament/get-status)}
       :put {:summary    "Update the tournament status."
             :openapi    {:tags         ["tournament"]
                          :produces     ["application/json"]
                          :operation-id "tournament-status/update"}
             :parameters {:path schema.contract/id-path-parameter
                          :body domain/update-status-specification}
             :responses  {200 {:body domain/tournament-advance-response}}
             :handler    (integrant.core/ref ::web.tournament/update-status)}}]
     ["/registration"
      {:get   {:summary    "Get the tournament registration window."
               :openapi    {:tags         ["tournament"]
                            :produces     ["application/json"]
                            :operation-id "tournament-registration/get"}
               :parameters {:path schema.contract/id-path-parameter}
               :responses  {200 {:body domain/tournament-registration-response}}
               :handler    (integrant.core/ref ::web.tournament/get-registration)}
       :patch {:summary    "Update the tournament registration window."
               :openapi    {:tags         ["tournament"]
                            :produces     ["application/json"]
                            :operation-id "tournament-registration/update"}
               :parameters {:path schema.contract/id-path-parameter
                            :body domain/update-registration-specification}
               :handler    (integrant.core/ref ::web.tournament/update-registration)}}]
     ["/match"
      [""
       {:get  {:summary    "List matches for a tournament."
               :openapi    {:tags         ["tournament"]
                            :produces     ["application/json"]
                            :operation-id "tournament-match/list"}
               :parameters {:path schema.contract/id-path-parameter}
               :responses  {200 {:body domain/tournament-matches-response}}
               :handler    (integrant.core/ref ::web.tournament/get-matches)}
        :post {:summary    "Create a match within a tournament."
               :openapi    {:tags         ["tournament"]
                            :produces     ["application/json"]
                            :operation-id "tournament-match/create"}
               :parameters {:path schema.contract/id-path-parameter
                            :body domain/create-match-specification}
               :handler    (integrant.core/ref ::web.tournament/create-match)}}]
      ["/:match-eid"
       {:name :match/by-eid
        :get  {:summary    "Get a match by eid."
               :openapi    {:tags         ["tournament"]
                            :produces     ["application/json"]
                            :operation-id "tournament-match/get"}
               :parameters {:path (schema.contract/to-schema
                                   [:map
                                    [:eid :uuid]
                                    [:match-eid :uuid]])}
               :responses  {200 {:body domain/match-resource}}
               :handler    (integrant.core/ref ::web.tournament/get-match)}}]
      ["/:match-eid/result"
       {:put {:summary    "Record a match result."
              :openapi    {:tags         ["tournament"]
                           :produces     ["application/json"]
                           :operation-id "tournament-match/record-result"}
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:eid :uuid]
                                   [:match-eid :uuid]])
                           :body domain/record-result-specification}
              :responses  {200 {:body domain/tournament-match-result-response}}
              :handler    (integrant.core/ref ::web.tournament/update-match-result)}}]
      ["/:match-eid/game"
       {:get  {:summary    "List games for a match."
               :openapi    {:tags         ["tournament"]
                            :produces     ["application/json"]
                            :operation-id "tournament-game/list"}
               :parameters {:path (schema.contract/to-schema
                                   [:map
                                    [:eid :uuid]
                                    [:match-eid :uuid]])}
               :handler    (integrant.core/ref ::web.tournament/get-games)}
        :post {:summary    "Record a game result within a match."
               :openapi    {:tags         ["tournament"]
                            :produces     ["application/json"]
                            :operation-id "tournament-game/record"}
               :parameters {:path (schema.contract/to-schema
                                   [:map
                                    [:eid :uuid]
                                    [:match-eid :uuid]])
                            :body domain/record-result-specification}
               :handler    (integrant.core/ref ::web.tournament/record-game)}}]]
     ["/phase"
      {:name :tournament/phase-configuration
       :put  {:summary    "Update the tournament phase configuration."
              :openapi    {:tags         ["tournament"]
                           :produces     ["application/json"]
                           :operation-id "tournament-phase/update-configuration"}
              :parameters {:path schema.contract/id-path-parameter
                           :body domain/configure-phases-specification}
              :handler    (integrant.core/ref ::web.tournament/update-phase-configuration)}}]
     ["/phase/:phase-index"
      {:name :tournament/phase
       :get  {:summary    "Phase details (standings + bracket / rounds)."
              :openapi    {:tags         ["tournament"]
                           :produces     ["application/json" "application/htmx+html"]
                           :operation-id "tournament-phase/get"}
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:eid :uuid]
                                   [:phase-index :int]])}
              :responses  {200 {:body domain/phase-response}}
              :handler    (integrant.core/ref ::web.tournament/get-phase)}}]
     ["/round"
      {:name :tournament/round
       :get  {:summary    "Form partial for a tournament round."
              :openapi    {:tags         ["tournament"]
                           :produces     ["application/json" "application/htmx+html"]
                           :operation-id "tournament-round/get"}
              :parameters {:path schema.contract/id-path-parameter}
              :responses  {200 {:body domain/round-response}}
              :handler    (integrant.core/ref ::web.tournament/get-round)}
       :post {:summary    "Create the next round of matches for the current phase."
              :openapi    {:tags         ["tournament"]
                           :produces     ["application/json"]
                           :operation-id "tournament-round/create"}
              :parameters {:path schema.contract/id-path-parameter}
              :handler    (integrant.core/ref ::web.tournament/create-round)}}]]]

   ["/league"
    [""
     {:name :league/for-game
      :get  {:summary    "Fetches leagues for a game."
             :openapi    {:tags         ["league"]
                          :produces     ["application/json"]
                          :operation-id "league/for-game"}
             :parameters {:query (schema.contract/to-schema
                                  [:map
                                   [:game-eid :uuid]])}
             :responses  {200 {:body domain/league-collection-resource}}
             :handler    (integrant.core/ref ::web.league/get-leagues)}}]
    ["/:eid"
     {:name :league/by-eid
      :get  {:summary    "Fetches a league by eid."
             :openapi    {:tags         ["league"]
                          :produces     ["application/json"]
                          :operation-id "league/by-eid"}
             :parameters {:path schema.contract/id-path-parameter}
             :responses  {200 {:body domain/league-resource}}
             :handler    (integrant.core/ref ::web.league/get-league)}
      :put  {:summary    "Creates a league with the given eid."
             :openapi    {:tags         ["league"]
                          :produces     ["application/json"]
                          :operation-id "league/create"}
             :parameters {:path  schema.contract/id-path-parameter
                          :query schema.contract/version-query-parameter
                          :body  domain/create-league-specification}
             :responses  {201 {:body domain/league-resource}}
             :handler    (integrant.core/ref ::web.league/create-league)}}]
    ["/:league-eid/season"
     {:name :season/for-league
      :get  {:summary    "Fetches seasons for a league."
             :openapi    {:tags         ["league"]
                          :produces     ["application/json"]
                          :operation-id "season/for-league"}
             :parameters {:path (schema.contract/to-schema
                                 [:map [:league-eid :uuid]])}
             :responses  {200 {:body domain/season-collection-resource}}
             :handler    (integrant.core/ref ::web.season/get-seasons-for-league)}}]]
   ["/season/:eid"
    {:name :season/by-eid
     :get  {:summary    "Fetches a season by eid."
            :openapi    {:tags         ["season"]
                         :produces     ["application/json"]
                         :operation-id "season/by-eid"}
            :parameters {:path schema.contract/id-path-parameter}
            :responses  {200 {:body domain/season-resource}}
            :handler    (integrant.core/ref ::web.season/get-season)}}]
   ["/season/:league-eid/:eid"
    {:put {:summary    "Creates a season under a league."
           :openapi    {:tags         ["season"]
                        :produces     ["application/json"]
                        :operation-id "season/create"}
           :parameters {:path (schema.contract/to-schema
                               [:map
                                [:league-eid :uuid]
                                [:eid :uuid]])
                        :body domain/create-season-specification}
           :responses  {201 {:body domain/season-resource}
                        422 {:body domain/season-error-response}}
           :handler    (integrant.core/ref ::web.season/create-season)}}]
   ["/stats/game/:game-eid/faction"
    {:name :stats/game-faction
     :get  {:summary    "Faction win/loss standings across all matches for a game."
            :openapi    {:tags         ["stats"]
                         :produces     ["application/json"]
                         :operation-id "stats/game-faction"}
            :parameters {:path (schema.contract/to-schema
                                [:map [:game-eid :uuid]])}
            :responses  {200 {:body domain/faction-standings-response}}
            :handler    (integrant.core/ref ::web.stats/get-game-faction-standings)}}]
   ["/stats/league/:league-eid/faction"
    {:name :stats/league-faction
     :get  {:summary    "Faction win/loss standings across all matches in a league."
            :openapi    {:tags         ["stats"]
                         :produces     ["application/json"]
                         :operation-id "stats/league-faction"}
            :parameters {:path (schema.contract/to-schema
                                [:map [:league-eid :uuid]])}
            :responses  {200 {:body domain/faction-standings-response}}
            :handler    (integrant.core/ref ::web.stats/get-league-faction-standings)}}]
   ["/stats/season/:season-eid/faction"
    {:name :stats/season-faction
     :get  {:summary    "Faction win/loss standings across all matches in a season."
            :openapi    {:tags         ["stats"]
                         :produces     ["application/json"]
                         :operation-id "stats/season-faction"}
            :parameters {:path (schema.contract/to-schema
                                [:map [:season-eid :uuid]])}
            :responses  {200 {:body domain/faction-standings-response}}
            :handler    (integrant.core/ref ::web.stats/get-season-faction-standings)}}]

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
