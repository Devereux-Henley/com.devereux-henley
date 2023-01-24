(ns com.devereux-henley.rts-api.configuration
  (:require
   [com.devereux-henley.rts-api.db :as db]
   [com.devereux-henley.rts-api.web :as web]
   [com.devereux-henley.rts-api.web.game :as web.game]
   [com.devereux-henley.rts-api.web.routes :as web.routes]
   [com.devereux-henley.rts-api.web.social-media :as web.social-media]
   [com.devereux-henley.rts-api.web.tournament :as web.tournament]
   [com.devereux-henley.rts-api.web.view :as web.view]
   [integrant.core]))

(def port
  (if-let [port-property (System/getenv "RTS_API_PORT")]
    (Integer/parseInt port-property) ;; If this cannot be parsed, let it fail during startup.
    3001))

(def hostname (or (System/getenv "RTS_API_HOSTNAME") "http://localhost:3001"))
(def auth-hostname (or (System/getenv "AUTH_HOSTNAME") "http://localhost:4000"))
(def session-name (str "ory_session_" (or (System/getenv "AUTH_SLUG") "eloquentyalowwhtijq6my4")))
(def default-api-dependencies {:hostname   hostname
                               :connection (integrant.core/ref ::db/connection)})
(def default-view-dependencies {:session-name session-name})
(def core-configuration
  {::db/connection                              {}
   ::web/swagger-handler                        {}
   ::web.view/dashboard-view                    default-view-dependencies
   ::web.view/game-view                         default-view-dependencies
   ::web.view/about-view                        default-view-dependencies
   ::web.view/contact-view                      default-view-dependencies
   ::web.view/login-view                        {:auth-hostname auth-hostname}
   ::web.view/logout-view                       {:auth-hostname auth-hostname}
   ::web.game/get-game                          default-api-dependencies
   ::web.game/get-games                         default-api-dependencies
   ::web.game/get-faction                       default-api-dependencies
   ::web.game/get-game-social-link              default-api-dependencies
   ::web.social-media/get-platform              default-api-dependencies
   ::web.tournament/get-tournament              default-api-dependencies
   ::web.tournament/get-tournament-snapshot     default-api-dependencies
   ::web.tournament/get-tournaments             default-api-dependencies
   ::web.tournament/create-tournament           default-api-dependencies
   ::web.routes/routes
   [web.routes/root-route
    web.routes/icon-routes
    web.routes/view-routes
    web.routes/api-routes]
   ::web/app                                    {:routes (integrant.core/ref ::web.routes/routes)
                                                 :session-name session-name
                                                 :auth-hostname auth-hostname}
   ::web/service                                {:handler       (integrant.core/ref ::web/app)
                                                 :configuration {:port port, :join? false}}})
