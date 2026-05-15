(ns com.devereux-henley.rts-web.web.routes
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.orchestration :as orchestration]
   [com.devereux-henley.rts-web.web.actions.draft :as web.actions.draft]
   [com.devereux-henley.rts-web.web.api :as web.api]
   [com.devereux-henley.rts-web.web.actions.league :as web.actions.league]
   [com.devereux-henley.rts-web.web.actions.season :as web.actions.season]
   [com.devereux-henley.rts-web.web.actions.tournament :as web.actions.tournament]
   [com.devereux-henley.rts-web.web.asset.api :as web.asset.api]
   [com.devereux-henley.rts-web.web.draft.api :as web.draft.api]
   [com.devereux-henley.rts-web.web.draft.view :as web.draft.view]
   [com.devereux-henley.rts-web.web.game.api :as web.game.api]
   [com.devereux-henley.rts-web.web.game.view :as web.game.view]
   [com.devereux-henley.rts-web.web.league.api :as web.league.api]
   [com.devereux-henley.rts-web.web.league.view :as web.league.view]
   [com.devereux-henley.rts-web.web.season.api :as web.season.api]
   [com.devereux-henley.rts-web.web.season.view :as web.season.view]
   [com.devereux-henley.rts-web.web.social-media.api :as web.social-media.api]
   [com.devereux-henley.rts-web.web.stats.api :as web.stats.api]
   [com.devereux-henley.rts-web.web.tournament.api :as web.tournament.api]
   [com.devereux-henley.rts-web.web.tournament.view :as web.tournament.view]
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
          :handler    web.asset.api/icon-handler}}])

(def view-routes
  ["/view"
   {:no-doc     true
    :middleware [(integrant.core/ref ::orchestration/middleware)]}
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
   ["/match-record/:match-eid"
    ["/index.html"
     {:get {:produces   ["text/html" "application/htmx+html"]
            :parameters {:path (schema.contract/to-schema [:map [:match-eid :uuid]])}
            :handler    (integrant.core/ref ::web.tournament.view/modal-view)}}]
    ["/parse"
     {:post {:summary    "Parse uploaded replays and return the review-step fragment."
             :openapi    {:tags         ["match-record"]
                          :consumes     ["multipart/form-data"]
                          :produces     ["text/html"]
                          :operation-id "match-record/parse-fragment"}
             :parameters {:path      (schema.contract/to-schema [:map [:match-eid :uuid]])
                          :multipart (schema.contract/to-schema [:map {:closed false}])}
             :handler    (integrant.core/ref ::web.tournament.view/parse-replays-fragment)}}]
    ["/submit"
     {:post {:summary    "Commit a parsed-replay submission and return the submitted-step fragment."
             :openapi    {:tags         ["match-record"]
                          :consumes     ["application/x-www-form-urlencoded"]
                          :produces     ["text/html"]
                          :operation-id "match-record/submit-fragment"}
             :parameters {:path (schema.contract/to-schema [:map [:match-eid :uuid]])}
             :handler    (integrant.core/ref ::web.tournament.view/record-match-fragment)}}]]
   ["/game/:game-eid"
    {:middleware [(integrant.core/ref ::web.view/game-context-middleware)]}
    ["/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/game-id-path-parameter}
            :handler    (integrant.core/ref ::web.game.view/game-index-view)}}]
    ["/faction/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/game-id-path-parameter}
            :handler    (integrant.core/ref ::web.game.view/faction-list-view)}}]
    ["/faction/:eid/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/game-and-id-path-parameter}
            :handler    (integrant.core/ref ::web.game.view/faction-view)}}]
    ["/unit/:eid/index.html"
     {:get {:produces   ["application/htmx+html"]
            :parameters {:path  schema.contract/game-and-id-path-parameter
                         :query (schema.contract/to-schema
                                 [:map
                                  [:lore {:optional true} [:maybe :string]]])}
            :handler    (integrant.core/ref ::web.game.view/unit-view)}}]
    ["/draft"
     ["/create.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.draft.view/create-draft-view)}}]
     ["/me.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path  schema.contract/game-id-path-parameter
                          :query (schema.contract/to-schema
                                  [:map
                                   [:faction {:optional true} :string]])}
             :handler    (integrant.core/ref ::web.draft.view/my-drafts-view)}}]
     ["/:eid/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.draft.view/draft-view)}}]]
    ["/tournament"
     ["/create.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.tournament.view/create-tournament-view)}}]
     ["/:eid/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.tournament.view/tournament-view)}}]
     ["/:eid/phase.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.tournament.view/tournament-phase-form-view)}}]]
    ["/competitive"
     ["/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.tournament.view/competitive-view)}}]
     ["/season-options.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path  schema.contract/game-id-path-parameter
                          :query (schema.contract/to-schema
                                  [:map
                                   [:league-eid {:optional true} [:maybe :uuid]]])}
             :handler    (integrant.core/ref ::web.season.view/season-options-fragment-view)}}]]
    ["/league"
     ["/create.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-id-path-parameter}
             :handler    (integrant.core/ref ::web.league.view/create-league-view)}}]
     ["/:eid/index.html"
      {:get {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/game-and-id-path-parameter}
             :handler    (integrant.core/ref ::web.league.view/league-view)}}]
     ["/:league-eid/season"
      ["/create.html"
       {:get {:produces   ["application/htmx+html"]
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:game-eid :uuid]
                                   [:league-eid :uuid]])}
              :handler    (integrant.core/ref ::web.season.view/create-season-view)}}]
      ["/:eid/index.html"
       {:get {:produces   ["application/htmx+html"]
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:game-eid :uuid]
                                   [:league-eid :uuid]
                                   [:eid :uuid]])}
              :handler    (integrant.core/ref ::web.season.view/season-view)}}]]]]])

(def components-routes
  ["/components"
   {:no-doc     true
    :middleware [(integrant.core/ref ::orchestration/middleware)]}
   ["/faction/:eid/faction-card.html"
    {:get {:produces   ["application/htmx+html"]
           :parameters {:path  schema.contract/id-path-parameter
                        :query web.game.api/faction-query-parameters}
           :responses  {200 {:body domain/faction-resource}}
           :handler    (integrant.core/ref ::web.game.api/get-faction)}}]
   ["/draft/:draft-eid/unit-panel.html"
    {:get {:produces   ["application/htmx+html"]
           :parameters {:path  (schema.contract/to-schema
                                [:map
                                 [:draft-eid :uuid]])
                        :query (schema.contract/to-schema
                                [:map
                                 [:unit-eid  :uuid]
                                 [:mount     {:optional true} [:maybe :string]]
                                 [:lore      {:optional true} [:maybe :string]]
                                 [:items     {:optional true} [:or :string [:sequential :string]]]
                                 [:spells    {:optional true} [:or :string [:sequential :string]]]
                                 [:abilities {:optional true} [:or :string [:sequential :string]]]])}
           :responses  {200 {:body domain/draft-unit-resource}
                        500 {:body domain/draft-error-response}}
           :handler    (integrant.core/ref ::web.draft.api/get-draft-unit)}}]
   ["/draft/:draft-eid/unit/:eid/unit-panel.html"
    {:get {:produces   ["application/htmx+html"]
           :parameters {:path  (schema.contract/to-schema
                                [:map
                                 [:draft-eid :uuid]
                                 [:eid :uuid]])
                        :query (schema.contract/to-schema
                                [:map
                                 [:mount     {:optional true} [:maybe :string]]
                                 [:level     {:optional true} [:int {:min 0 :max 9}]]
                                 [:items     {:optional true} [:or :string [:sequential :string]]]
                                 [:spells    {:optional true} [:or :string [:sequential :string]]]
                                 [:abilities {:optional true} [:or :string [:sequential :string]]]])}
           :responses  {200 {:body domain/draft-unit-resource}
                        500 {:body domain/draft-error-response}}
           :handler    (integrant.core/ref ::web.draft.api/get-draft-unit)}}]
   ["/draft/:draft-eid/entry/:eid/entry-panel.html"
    {:get {:produces   ["application/htmx+html"]
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
                                 [:level     {:optional true} [:int {:min 0 :max 9}]]
                                 [:items     {:optional true} [:or :string [:sequential :string]]]
                                 [:spells    {:optional true} [:or :string [:sequential :string]]]
                                 [:abilities {:optional true} [:or :string [:sequential :string]]]])}
           :responses  {200 {:body domain/draft-entry-resource}
                        404 {:body domain/draft-error-response}
                        500 {:body domain/draft-error-response}}
           :handler    (integrant.core/ref ::web.draft.api/get-draft-entry)}}]
   ["/tournament/:eid/round-row.html"
    {:get {:produces   ["application/htmx+html"]
           :parameters {:path schema.contract/id-path-parameter}
           :responses  {200 {:body domain/round-response}}
           :handler    (integrant.core/ref ::web.tournament.api/get-round)}}]
   ["/tournament/:eid/phase/:phase-index/phase-panel.html"
    {:get {:produces   ["application/htmx+html"]
           :parameters {:path (schema.contract/to-schema
                               [:map
                                [:eid :uuid]
                                [:phase-index :int]])}
           :responses  {200 {:body domain/phase-response}}
           :handler    (integrant.core/ref ::web.tournament.api/get-phase)}}]])

(def actions-routes
  ["/actions"
   {:no-doc true}
   ["/draft/:draft-eid/unit/:eid"
    {:post {:produces   ["application/htmx+html"]
            :parameters {:path  (schema.contract/to-schema
                                 [:map
                                  [:draft-eid :uuid]
                                  [:eid :uuid]])
                         :query (schema.contract/to-schema
                                 [:map
                                  [:section [:enum "main" "reinforcements"]]])
                         :body  domain/add-unit-to-draft-specification}
            :handler    (integrant.core/ref ::web.actions.draft/add-unit)}}]
   ["/draft/:draft-eid/entry/:eid"
    {:patch  {:produces   ["application/htmx+html"]
              :parameters {:path  (schema.contract/to-schema
                                   [:map
                                    [:draft-eid :uuid]
                                    [:eid :uuid]])
                           :query (schema.contract/to-schema
                                   [:map
                                    [:section [:enum "main" "reinforcements"]]])
                           :body  domain/add-unit-to-draft-specification}
              :handler    (integrant.core/ref ::web.actions.draft/update-entry)}
     :delete {:produces   ["application/htmx+html"]
              :parameters {:path  (schema.contract/to-schema
                                   [:map
                                    [:draft-eid :uuid]
                                    [:eid :uuid]])
                           :query (schema.contract/to-schema
                                   [:map
                                    [:section [:enum "main" "reinforcements"]]])}
              :handler    (integrant.core/ref ::web.actions.draft/remove-entry)}}]
   ["/draft/:eid"
    {:put   {:produces   ["application/htmx+html"]
             :parameters {:path  schema.contract/id-path-parameter
                          :query schema.contract/version-query-parameter
                          :body  domain/create-draft-specification}
             :handler    (integrant.core/ref ::web.actions.draft/create-draft)}
     :patch {:produces   ["application/htmx+html"]
             :parameters {:path schema.contract/id-path-parameter
                          :body domain/update-draft-specification}
             :handler    (integrant.core/ref ::web.actions.draft/update-draft)}}]
   ["/tournament/:eid"
    {:put {:produces   ["application/htmx+html"]
           :parameters {:path  schema.contract/id-path-parameter
                        :query schema.contract/version-query-parameter
                        :body  domain/create-tournament-specification}
           :handler    (integrant.core/ref ::web.actions.tournament/create-tournament)}}]
   ["/tournament/:eid/entry/me"
    {:post   {:produces   ["application/htmx+html"]
              :parameters {:path schema.contract/id-path-parameter}
              :handler    (integrant.core/ref ::web.actions.tournament/create-entry)}
     :delete {:produces   ["application/htmx+html"]
              :parameters {:path schema.contract/id-path-parameter}
              :handler    (integrant.core/ref ::web.actions.tournament/delete-entry)}}]
   ["/tournament/:eid/start"
    {:post {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/id-path-parameter}
            :handler    (integrant.core/ref ::web.actions.tournament/start-tournament)}}]
   ["/tournament/:eid/complete"
    {:post {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/id-path-parameter}
            :handler    (integrant.core/ref ::web.actions.tournament/complete-tournament)}}]
   ["/tournament/:eid/cancel"
    {:post {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/id-path-parameter}
            :handler    (integrant.core/ref ::web.actions.tournament/cancel-tournament)}}]
   ["/tournament/:eid/close-registration"
    {:post {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/id-path-parameter}
            :handler    (integrant.core/ref ::web.actions.tournament/close-registration)}}]
   ["/tournament/:eid/round"
    {:post {:produces   ["application/htmx+html"]
            :parameters {:path schema.contract/id-path-parameter}
            :handler    (integrant.core/ref ::web.actions.tournament/create-round)}}]
   ["/tournament/:tournament-eid/match"
    {:post {:produces   ["application/htmx+html"]
            :parameters {:path (schema.contract/to-schema
                                [:map
                                 [:tournament-eid :uuid]])
                         :body domain/create-match-specification}
            :handler    (integrant.core/ref ::web.actions.tournament/create-match)}}]
   ["/tournament/:tournament-eid/match/:eid/result"
    {:put {:produces   ["application/htmx+html"]
           :parameters {:path (schema.contract/to-schema
                               [:map
                                [:tournament-eid :uuid]
                                [:eid :uuid]])
                        :body domain/record-result-specification}
           :handler    (integrant.core/ref ::web.actions.tournament/update-match-result)}}]
   ["/tournament/:tournament-eid/match/:eid/game"
    {:post {:produces   ["application/htmx+html"]
            :parameters {:path (schema.contract/to-schema
                                [:map
                                 [:tournament-eid :uuid]
                                 [:eid :uuid]])
                         :body domain/record-result-specification}
            :handler    (integrant.core/ref ::web.actions.tournament/record-game)}}]
   ["/league/:eid"
    {:put {:produces   ["application/htmx+html"]
           :parameters {:path  schema.contract/id-path-parameter
                        :query schema.contract/version-query-parameter
                        :body  domain/create-league-specification}
           :handler    (integrant.core/ref ::web.actions.league/create-league)}}]
   ["/season/:league-eid/:eid"
    {:put {:produces   ["application/htmx+html"]
           :parameters {:path (schema.contract/to-schema
                               [:map
                                [:league-eid :uuid]
                                [:eid :uuid]])
                        :body domain/create-season-specification}
           :handler    (integrant.core/ref ::web.actions.season/create-season)}}]])

(def api-routes
  ["/api"
   {:no-doc true}

   ;; ─── Root ───────────────────────────────────────────────────────────────
   [""
    {:get {:no-doc   true
           :produces ["text/html"]
           :handler  (fn [_request] {:status 301 :headers {"Location" "/api/index.html"}})}}]
   ["/index.html"
    {:name :api/root
     :get  {:produces  ["text/html"]
            :responses {200 {:body (schema.contract/to-schema
                                    [:map
                                     [:type [:= :api/root]]
                                     [:_links [:map
                                               [:self :url]
                                               [:games :url]]]])}}
            :handler   (integrant.core/ref ::web.api/get-root)}}]

   ;; ─── Game ───────────────────────────────────────────────────────────────
   ["/game"
    {:name :collection/game
     :get  {:produces  ["text/html"]
            :responses {200 {:body domain/game-collection-resource}}
            :handler   (integrant.core/ref ::web.game.api/get-games)}}]
   ["/game/:eid"
    {:name :game/by-eid
     :get  {:produces   ["text/html"]
            :parameters {:path  schema.contract/id-path-parameter
                         :query web.game.api/game-query-parameters}
            :responses  {200 {:body domain/game-resource}}
            :handler    (integrant.core/ref ::web.game.api/get-game)}}]
   ["/game/social-link/:eid"
    {:name :game/social-by-eid
     :get  {:produces   ["text/html"]
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body domain/game-social-link-resource}}
            :handler    (integrant.core/ref ::web.game.api/get-game-social-link)}}]
   ["/game/faction/:eid"
    {:name :game/faction-by-eid
     :get  {:produces   ["text/html"]
            :parameters {:path  schema.contract/id-path-parameter
                         :query web.game.api/faction-query-parameters}
            :responses  {200 {:body domain/faction-resource}}
            :handler    (integrant.core/ref ::web.game.api/get-faction)}}]

   ;; ─── Draft (reads only — mutations live on /actions) ────────────────────
   ["/draft/:eid"
    {:name :draft/by-eid
     :get  {:produces   ["text/html"]
            :parameters {:path schema.contract/id-path-parameter}
            :responses  {200 {:body domain/draft-resource}}
            :handler    (integrant.core/ref ::web.draft.api/get-draft)}}]
   ["/draft/:draft-eid/unit"
    {:name :draft-unit/preview
     :get  {:produces   ["text/html"]
            :parameters {:path  (schema.contract/to-schema
                                 [:map
                                  [:draft-eid :uuid]])
                         :query (schema.contract/to-schema
                                 [:map
                                  [:unit-eid  :uuid]
                                  [:mount     {:optional true} [:maybe :string]]
                                  [:lore      {:optional true} [:maybe :string]]
                                  [:items     {:optional true} [:or :string [:sequential :string]]]
                                  [:spells    {:optional true} [:or :string [:sequential :string]]]
                                  [:abilities {:optional true} [:or :string [:sequential :string]]]])}
            :responses  {200 {:body domain/draft-unit-resource}}
            :handler    (integrant.core/ref ::web.draft.api/get-draft-unit)}}]
   ["/draft/:draft-eid/unit/:eid"
    {:name :draft-unit/by-eid
     :get  {:produces   ["text/html"]
            :parameters {:path  (schema.contract/to-schema
                                 [:map
                                  [:draft-eid :uuid]
                                  [:eid :uuid]])
                         :query (schema.contract/to-schema
                                 [:map
                                  [:mount     {:optional true} [:maybe :string]]
                                  [:level     {:optional true} [:int {:min 0 :max 9}]]
                                  [:items     {:optional true} [:or :string [:sequential :string]]]
                                  [:spells    {:optional true} [:or :string [:sequential :string]]]
                                  [:abilities {:optional true} [:or :string [:sequential :string]]]])}
            :responses  {200 {:body domain/draft-unit-resource}}
            :handler    (integrant.core/ref ::web.draft.api/get-draft-unit)}}]
   ["/draft/:draft-eid/entry/:eid"
    {:name :draft-entry/by-eid
     :get  {:produces   ["text/html"]
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
                                  [:level     {:optional true} [:int {:min 0 :max 9}]]
                                  [:items     {:optional true} [:or :string [:sequential :string]]]
                                  [:spells    {:optional true} [:or :string [:sequential :string]]]
                                  [:abilities {:optional true} [:or :string [:sequential :string]]]])}
            :responses  {200 {:body domain/draft-entry-resource}}
            :handler    (integrant.core/ref ::web.draft.api/get-draft-entry)}}]

   ;; ─── Tournament reads ──────────────────────────────────────────────────
   ["/tournament"
    [""
     {:name :tournament/for-game
      :get  {:produces   ["text/html"]
             :parameters {:query (schema.contract/to-schema
                                  [:map [:game-eid :uuid]])}
             :responses  {200 {:body domain/tournament-collection-resource}}
             :handler    (integrant.core/ref ::web.tournament.api/get-tournaments)}}]
    ["/:eid"
     [""
      {:name :tournament/by-eid
       :get  {:produces   ["text/html"]
              :parameters {:path schema.contract/id-path-parameter}
              :responses  {200 {:body domain/tournament-resource}}
              :handler    (integrant.core/ref ::web.tournament.api/get-tournament)}}]
     ["/entry"
      [""
       {:name :tournament/entries
        :get  {:produces   ["text/html"]
               :parameters {:path schema.contract/id-path-parameter}
               :responses  {200 {:body domain/tournament-entries-response}}
               :handler    (integrant.core/ref ::web.tournament.api/get-entries)}}]]
     ["/status"
      {:name :tournament/status
       :get  {:produces   ["text/html"]
              :parameters {:path schema.contract/id-path-parameter}
              :responses  {200 {:body domain/tournament-status-response}}
              :handler    (integrant.core/ref ::web.tournament.api/get-status)}}]
     ["/registration"
      {:name :tournament/registration
       :get  {:produces   ["text/html"]
              :parameters {:path schema.contract/id-path-parameter}
              :responses  {200 {:body domain/tournament-registration-response}}
              :handler    (integrant.core/ref ::web.tournament.api/get-registration)}}]
     ["/phase"
      {:name :tournament/phase-configuration
       :put  {:produces   ["text/html"]
              :parameters {:path schema.contract/id-path-parameter
                           :body domain/configure-phases-specification}
              :handler    (integrant.core/ref ::web.tournament.api/update-phase-configuration)}}]
     ["/phase/:phase-index"
      {:name :tournament/phase
       :get  {:produces   ["text/html"]
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:eid :uuid]
                                   [:phase-index :int]])}
              :responses  {200 {:body domain/phase-response}}
              :handler    (integrant.core/ref ::web.tournament.api/get-phase)}}]
     ["/round"
      {:name :tournament/round
       :get  {:produces   ["text/html"]
              :parameters {:path schema.contract/id-path-parameter}
              :responses  {200 {:body domain/round-response}}
              :handler    (integrant.core/ref ::web.tournament.api/get-round)}}]]
    ["/:tournament-eid/match"
     [""
      {:name :tournament/matches
       :get  {:produces   ["text/html"]
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:tournament-eid :uuid]])}
              :responses  {200 {:body domain/tournament-matches-response}}
              :handler    (integrant.core/ref ::web.tournament.api/get-matches)}}]
     ["/:eid"
      {:name :match/by-eid
       :get  {:produces   ["text/html"]
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:tournament-eid :uuid]
                                   [:eid :uuid]])}
              :responses  {200 {:body domain/match-resource}}
              :handler    (integrant.core/ref ::web.tournament.api/get-match)}}]
     ["/:eid/game"
      {:name :match/games
       :get  {:produces   ["text/html"]
              :parameters {:path (schema.contract/to-schema
                                  [:map
                                   [:tournament-eid :uuid]
                                   [:eid :uuid]])}
              :responses  {200 {:body domain/tournament-games-response}}
              :handler    (integrant.core/ref ::web.tournament.api/get-games)}}]]]

   ;; ─── League ────────────────────────────────────────────────────────────
   ["/league"
    [""
     {:name :league/for-game
      :get  {:produces   ["text/html"]
             :parameters {:query (schema.contract/to-schema
                                  [:map [:game-eid :uuid]])}
             :responses  {200 {:body domain/league-collection-resource}}
             :handler    (integrant.core/ref ::web.league.api/get-leagues)}}]
    ["/:eid"
     {:name :league/by-eid
      :get  {:produces   ["text/html"]
             :parameters {:path schema.contract/id-path-parameter}
             :responses  {200 {:body domain/league-resource}}
             :handler    (integrant.core/ref ::web.league.api/get-league)}}]
    ["/:league-eid/season"
     {:name :season/for-league
      :get  {:produces   ["text/html"]
             :parameters {:path (schema.contract/to-schema
                                 [:map [:league-eid :uuid]])}
             :responses  {200 {:body domain/season-collection-resource}}
             :handler    (integrant.core/ref ::web.season.api/get-seasons-for-league)}}]]
   ["/season/:eid"
    {:name :season/by-eid
     :get  {:produces   ["text/html"]
            :parameters {:path schema.contract/id-path-parameter}
            :responses  {200 {:body domain/season-resource}}
            :handler    (integrant.core/ref ::web.season.api/get-season)}}]

   ;; ─── Stats ─────────────────────────────────────────────────────────────
   ["/stats/game/:game-eid/faction"
    {:name :stats/game-faction
     :get  {:produces   ["text/html"]
            :parameters {:path (schema.contract/to-schema
                                [:map [:game-eid :uuid]])}
            :responses  {200 {:body domain/faction-standings-response}}
            :handler    (integrant.core/ref ::web.stats.api/get-game-faction-standings)}}]
   ["/stats/league/:league-eid/faction"
    {:name :stats/league-faction
     :get  {:produces   ["text/html"]
            :parameters {:path (schema.contract/to-schema
                                [:map [:league-eid :uuid]])}
            :responses  {200 {:body domain/faction-standings-response}}
            :handler    (integrant.core/ref ::web.stats.api/get-league-faction-standings)}}]
   ["/stats/season/:season-eid/faction"
    {:name :stats/season-faction
     :get  {:produces   ["text/html"]
            :parameters {:path (schema.contract/to-schema
                                [:map [:season-eid :uuid]])}
            :responses  {200 {:body domain/faction-standings-response}}
            :handler    (integrant.core/ref ::web.stats.api/get-season-faction-standings)}}]

   ["/social-media/:eid"
    {:name :social-media/by-eid
     :get  {:produces   ["text/html"]
            :parameters {:path  schema.contract/id-path-parameter
                         :query schema.contract/version-query-parameter}
            :responses  {200 {:body domain/social-media-platform-resource}}
            :handler    (integrant.core/ref ::web.social-media.api/get-platform)}}]])

(defmethod integrant.core/init-key ::routes
  [_init-key routes]
  routes)
