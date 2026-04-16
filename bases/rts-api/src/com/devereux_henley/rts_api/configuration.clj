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
   [integrant.core]))

(def port
  (if-let [port-property (System/getenv "RTS_API_PORT")]
    (Integer/parseInt port-property) ;; If this cannot be parsed, let it fail during startup.
    3001))

(def hostname (or (System/getenv "RTS_API_HOSTNAME") "http://localhost:3001"))
(def auth-hostname (or (System/getenv "AUTH_HOSTNAME") "http://localhost:4000"))
(def session-name (str "ory_session_" (or (System/getenv "AUTH_SLUG") "eloquentyalowwhtijq6my4")))
(def default-dependencies {:hostname   hostname
                           :connection (integrant.core/ref ::db/connection)})

;;; ─── Per-namespace handler configuration ───────────────────────────────────

(def infrastructure-configuration
  {::rts-data/migrate   {:db-spec       db/db-spec
                         :migration-dir rts-data/migration-dir}
   ::db/connection      {:migrations (integrant.core/ref ::rts-data/migrate)}
   ::web/openapi-handler {}
   ::web/service        {:handler       (integrant.core/ref ::web/app)
                         :configuration {:port port, :join? false}}
   :com.devereux-henley.rts-web.web.configuration/configuration
   {:com.devereux-henley.rts-web.web.configuration/openid-url    (str auth-hostname "/" ".well-known/openid-configuration")
    :com.devereux-henley.rts-web.web.configuration/auth-hostname auth-hostname}})

(def view-configuration
  {:com.devereux-henley.rts-web.web.view/dashboard-view          default-dependencies
   :com.devereux-henley.rts-web.web.view/game-view               default-dependencies
   :com.devereux-henley.rts-web.web.view/about-view              default-dependencies
   :com.devereux-henley.rts-web.web.view/contact-view            default-dependencies
   :com.devereux-henley.rts-web.web.view/login-view              {:auth-hostname auth-hostname}
   :com.devereux-henley.rts-web.web.view/logout-view             {:auth-hostname auth-hostname}
   :com.devereux-henley.rts-web.web.view/game-context-middleware default-dependencies
   :com.devereux-henley.rts-web.web.view/game-index-view         default-dependencies
   :com.devereux-henley.rts-web.web.view/faction-view            default-dependencies
   :com.devereux-henley.rts-web.web.view/unit-view               default-dependencies
   :com.devereux-henley.rts-web.web.view/draft-view              default-dependencies
   :com.devereux-henley.rts-web.web.view/my-drafts-view          default-dependencies
   :com.devereux-henley.rts-web.web.view/create-draft-view       default-dependencies})

(def game-configuration
  {:com.devereux-henley.rts-web.web.game/get-game                default-dependencies
   :com.devereux-henley.rts-web.web.game/get-games               default-dependencies
   :com.devereux-henley.rts-web.web.game/get-faction             default-dependencies
   :com.devereux-henley.rts-web.web.game/get-game-social-link    default-dependencies
   :com.devereux-henley.rts-web.web.game/create-draft            default-dependencies})

(def draft-configuration
  {:com.devereux-henley.rts-web.web.draft/get-draft-unit         default-dependencies
   :com.devereux-henley.rts-web.web.draft/get-draft-entry        default-dependencies
   :com.devereux-henley.rts-web.web.draft/draft-add-unit         default-dependencies
   :com.devereux-henley.rts-web.web.draft/draft-update-unit      default-dependencies
   :com.devereux-henley.rts-web.web.draft/draft-remove-unit      default-dependencies})

(def social-media-configuration
  {:com.devereux-henley.rts-web.web.social-media/get-platform    default-dependencies})

;;; ─── Assembled system maps ─────────────────────────────────────────────────

(def base-configuration
  "Shared integrant wiring used by every profile. Auth middleware and the
   `::web/app` entry point are contributed by the individual profiles so they
   can swap Ory for a dev stub without copy-pasting the rest of the system."
  (merge infrastructure-configuration
         view-configuration
         game-configuration
         draft-configuration
         social-media-configuration))

(def core-configuration
  "Production profile. Uses Ory for authentication."
  (assoc base-configuration
         ::web/ory-auth-middleware
         {:auth-hostname auth-hostname
          :session-name  session-name}

         :com.devereux-henley.rts-web.web.routes/routes
         [rts-web/root-route
          rts-web/status-route
          rts-web/icon-routes
          rts-web/view-routes
          rts-web/api-routes]

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
         [rts-web/root-route
          rts-web/status-route
          rts-web/shutdown-route
          rts-web/icon-routes
          rts-web/view-routes
          rts-web/api-routes
          dev-auth/routes]

         ::web/app
         {:routes          (integrant.core/ref :com.devereux-henley.rts-web.web.routes/routes)
          :auth-middleware (integrant.core/ref ::dev-auth/impersonation-middleware)}))
