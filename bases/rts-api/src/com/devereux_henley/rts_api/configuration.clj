(ns com.devereux-henley.rts-api.configuration
  "Integrant system map. Exposes named profiles so the REPL (dev + Claude) can
   boot a development profile that stubs Ory authentication, while production
   jars still use `core-configuration` with real Ory wiring."
  (:require
   [com.devereux-henley.rts-api.db :as db]
   [com.devereux-henley.rts-api.dev-auth :as dev-auth]
   [com.devereux-henley.rts-api.web :as web]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [com.devereux-henley.rts-web.contract :as rts-web]
   [com.devereux-henley.rts-web.orchestration :as orchestration]
   [com.devereux-henley.rts-web.web.actions.draft :as web.actions.draft]
   [com.devereux-henley.rts-web.web.actions.tournament :as web.actions.tournament]
   [integrant.core]))

(def port
  (if-let [port-property (System/getenv "RTS_API_PORT")]
    (Integer/parseInt port-property) ;; If this cannot be parsed, let it fail during startup.
    3001))

(def hostname (or (System/getenv "RTS_API_HOSTNAME") "http://localhost:3001"))
(def auth-hostname (or (System/getenv "AUTH_HOSTNAME") "http://localhost:4000"))
(def session-name (str "ory_session_" (or (System/getenv "AUTH_SLUG") "eloquentyalowwhtijq6my4")))
(def replay-parser-bin (or (System/getenv "REPLAY_PARSER_BIN") "tw-replay-parser"))
(def default-dependencies {:hostname          hostname
                           :connection        (integrant.core/ref ::db/connection)
                           :replay-parser-bin replay-parser-bin})

(defn- handlers
  "Maps each Integrant key to default-dependencies."
  [& keys]
  (zipmap keys (repeat default-dependencies)))

;;; ─── Per-namespace handler configuration ───────────────────────────────────

(def infrastructure-configuration
  {::rts-data/migrate    {:db-spec       db/db-spec
                          :migration-dir rts-data/migration-dir}
   ::db/connection       {:migrations (integrant.core/ref ::rts-data/migrate)}
   ::web/openapi-handler {}
   ::web/service         {:handler       (integrant.core/ref ::web/app)
                          :configuration {:port port, :join? false}}
   :com.devereux-henley.rts-web.web.configuration/configuration
   {:com.devereux-henley.rts-web.web.configuration/openid-url    (str auth-hostname "/" ".well-known/openid-configuration")
    :com.devereux-henley.rts-web.web.configuration/auth-hostname auth-hostname}})

(def view-configuration
  (merge
   (handlers :com.devereux-henley.rts-web.web.view/game-selector-view
             :com.devereux-henley.rts-web.web.view/game-view
             :com.devereux-henley.rts-web.web.view/about-view
             :com.devereux-henley.rts-web.web.view/contact-view
             :com.devereux-henley.rts-web.web.view/game-context-middleware
             :com.devereux-henley.rts-web.web.game.view/game-index-view
             :com.devereux-henley.rts-web.web.game.view/faction-view
             :com.devereux-henley.rts-web.web.game.view/faction-list-view
             :com.devereux-henley.rts-web.web.game.view/unit-view
             :com.devereux-henley.rts-web.web.draft.view/draft-view
             :com.devereux-henley.rts-web.web.draft.view/my-drafts-view
             :com.devereux-henley.rts-web.web.draft.view/create-draft-view)
   {:com.devereux-henley.rts-web.web.view/login-view  {:auth-hostname auth-hostname}
    :com.devereux-henley.rts-web.web.view/logout-view {:auth-hostname auth-hostname}}))

(def game-configuration
  (handlers :com.devereux-henley.rts-web.web.game.api/get-game
            :com.devereux-henley.rts-web.web.game.api/get-games
            :com.devereux-henley.rts-web.web.game.api/get-faction
            :com.devereux-henley.rts-web.web.game.api/get-game-social-link))

(def draft-configuration
  (handlers :com.devereux-henley.rts-web.web.draft.api/get-draft-unit
            :com.devereux-henley.rts-web.web.draft.api/get-draft-entry
            :com.devereux-henley.rts-web.web.draft.api/draft-add-unit
            :com.devereux-henley.rts-web.web.draft.api/draft-update-unit
            :com.devereux-henley.rts-web.web.draft.api/draft-remove-unit
            :com.devereux-henley.rts-web.web.draft.api/create-draft
            :com.devereux-henley.rts-web.web.draft.api/update-draft))

(def social-media-configuration
  (handlers :com.devereux-henley.rts-web.web.social-media.api/get-platform))

(def tournament-configuration
  (handlers :com.devereux-henley.rts-web.web.tournament.api/get-tournament
            :com.devereux-henley.rts-web.web.tournament.api/get-tournaments
            :com.devereux-henley.rts-web.web.tournament.api/create-tournament
            :com.devereux-henley.rts-web.web.tournament.api/create-entry
            :com.devereux-henley.rts-web.web.tournament.api/delete-entry
            :com.devereux-henley.rts-web.web.tournament.api/get-entries
            :com.devereux-henley.rts-web.web.tournament.api/get-status
            :com.devereux-henley.rts-web.web.tournament.api/update-status
            :com.devereux-henley.rts-web.web.tournament.api/get-registration
            :com.devereux-henley.rts-web.web.tournament.api/update-registration
            :com.devereux-henley.rts-web.web.tournament.api/get-matches
            :com.devereux-henley.rts-web.web.tournament.api/get-match
            :com.devereux-henley.rts-web.web.tournament.api/create-match
            :com.devereux-henley.rts-web.web.tournament.api/update-match-result
            :com.devereux-henley.rts-web.web.tournament.api/record-game
            :com.devereux-henley.rts-web.web.tournament.api/get-games
            :com.devereux-henley.rts-web.web.tournament.api/update-phase-configuration
            :com.devereux-henley.rts-web.web.tournament.api/create-round
            :com.devereux-henley.rts-web.web.tournament.api/get-phase
            :com.devereux-henley.rts-web.web.tournament.api/get-round
            :com.devereux-henley.rts-web.web.tournament.view/create-tournament-view
            :com.devereux-henley.rts-web.web.tournament.view/tournament-view
            :com.devereux-henley.rts-web.web.tournament.view/tournament-phase-form-view))

(def match-record-configuration
  (handlers :com.devereux-henley.rts-web.web.tournament.view/modal-view
            :com.devereux-henley.rts-web.web.tournament.view/parse-replays-fragment
            :com.devereux-henley.rts-web.web.tournament.view/record-match-fragment))

(def actions-configuration
  (handlers :com.devereux-henley.rts-web.web.actions.draft/add-unit
            :com.devereux-henley.rts-web.web.actions.draft/update-entry
            :com.devereux-henley.rts-web.web.actions.draft/remove-entry
            :com.devereux-henley.rts-web.web.actions.draft/create-draft
            :com.devereux-henley.rts-web.web.actions.draft/update-draft
            :com.devereux-henley.rts-web.web.actions.tournament/create-tournament
            :com.devereux-henley.rts-web.web.actions.tournament/create-entry
            :com.devereux-henley.rts-web.web.actions.tournament/delete-entry
            :com.devereux-henley.rts-web.web.actions.tournament/update-status
            :com.devereux-henley.rts-web.web.actions.tournament/update-registration
            :com.devereux-henley.rts-web.web.actions.tournament/create-round
            :com.devereux-henley.rts-web.web.actions.league/create-league
            :com.devereux-henley.rts-web.web.actions.season/create-season))

(def orchestration-configuration
  "Wires the HX-Trigger registry. Each `::web-triggers` key contributes its
   `{event-id [event …]}` map; the registry init-key gathers them via
   `refset` (each contributing key derives from
   `::orchestration/web-trigger-source` in its own namespace) and compiles
   them into HTMX trigger strings. The middleware then assocs the
   assembled registry onto every request that flows through view /
   components routes."
  {::web.actions.draft/web-triggers      {}
   ::web.actions.tournament/web-triggers {}
   ::orchestration/registry              {:sources (integrant.core/refset ::orchestration/web-trigger-source)}
   ::orchestration/middleware            {:registry (integrant.core/ref ::orchestration/registry)}})

(def league-configuration
  (handlers :com.devereux-henley.rts-web.web.league.api/get-league
            :com.devereux-henley.rts-web.web.league.api/get-leagues
            :com.devereux-henley.rts-web.web.league.api/create-league
            :com.devereux-henley.rts-web.web.season.api/get-season
            :com.devereux-henley.rts-web.web.season.api/get-seasons-for-league
            :com.devereux-henley.rts-web.web.season.api/create-season
            :com.devereux-henley.rts-web.web.stats.api/get-game-faction-standings
            :com.devereux-henley.rts-web.web.stats.api/get-league-faction-standings
            :com.devereux-henley.rts-web.web.stats.api/get-season-faction-standings
            :com.devereux-henley.rts-web.web.tournament.view/competitive-view
            :com.devereux-henley.rts-web.web.season.view/season-options-fragment-view
            :com.devereux-henley.rts-web.web.league.view/create-league-view
            :com.devereux-henley.rts-web.web.league.view/league-view
            :com.devereux-henley.rts-web.web.season.view/create-season-view
            :com.devereux-henley.rts-web.web.season.view/season-view))

;;; ─── Routes ────────────────────────────────────────────────────────────────

(def base-routes
  [rts-web/root-route
   rts-web/status-route
   rts-web/icon-routes
   rts-web/view-routes
   rts-web/components-routes
   rts-web/actions-routes
   rts-web/api-routes])

;;; ─── Assembled system maps ─────────────────────────────────────────────────

(def base-configuration
  "Shared integrant wiring used by every profile. Auth middleware and the
   `::web/app` entry point are contributed by the individual profiles so they
   can swap Ory for a dev stub without copy-pasting the rest of the system."
  (merge infrastructure-configuration
         view-configuration
         game-configuration
         draft-configuration
         social-media-configuration
         tournament-configuration
         league-configuration
         match-record-configuration
         actions-configuration
         orchestration-configuration))

(def core-configuration
  "Production profile. Uses Ory for authentication."
  (assoc base-configuration
         ::web/ory-auth-middleware
         {:auth-hostname auth-hostname
          :session-name  session-name}

         :com.devereux-henley.rts-web.web.routes/routes
         base-routes

         ::web/app
         {:routes          (integrant.core/ref :com.devereux-henley.rts-web.web.routes/routes)
          :auth-middleware (integrant.core/ref ::web/ory-auth-middleware)}))

(def development-configuration
  "Dev profile. Swaps Ory for the cookie-based impersonation stub in
   `dev-auth` and mounts the `/dev/*` impersonation routes. Downstream
   handlers still see `:ory-session` on the request with the same shape as
   the real Ory whoami response."
  (assoc base-configuration
         ::dev-auth/users                    {}
         ::dev-auth/impersonation-middleware {:users           (integrant.core/ref ::dev-auth/users)
                                              :default-user-id dev-auth/default-user-id}
         ::dev-auth/impersonate-handler      {:users (integrant.core/ref ::dev-auth/users)}
         ::dev-auth/logout-handler           {}
         ::dev-auth/list-users-handler       {:users (integrant.core/ref ::dev-auth/users)}

         :com.devereux-henley.rts-web.web.routes/routes
         (into base-routes [rts-web/shutdown-route dev-auth/routes])

         ::web/app
         {:routes          (integrant.core/ref :com.devereux-henley.rts-web.web.routes/routes)
          :auth-middleware (integrant.core/ref ::dev-auth/impersonation-middleware)}))
