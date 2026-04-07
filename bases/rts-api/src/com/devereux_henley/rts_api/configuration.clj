(ns com.devereux-henley.rts-api.configuration
  (:require
   [com.devereux-henley.rts-api.db :as db]
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
(def default-api-dependencies {:hostname   hostname
                               :connection (integrant.core/ref ::db/connection)})
(def default-view-dependencies {})
(def core-configuration
  {::rts-data/migrate                                                      {:db-spec       db/db-spec
                                                                            :migration-dir rts-data/migration-dir}
   ::db/connection                                                         {:migrations (integrant.core/ref ::rts-data/migrate)}
   ::web/swagger-handler                                                   {}
   :com.devereux-henley.rts-web.web.view/dashboard-view                   default-view-dependencies
   :com.devereux-henley.rts-web.web.view/tournament-view                  default-view-dependencies
   :com.devereux-henley.rts-web.web.view/game-view                        default-view-dependencies
   :com.devereux-henley.rts-web.web.view/about-view                       default-view-dependencies
   :com.devereux-henley.rts-web.web.view/contact-view                     default-view-dependencies
   :com.devereux-henley.rts-web.web.view/login-view                       {:auth-hostname auth-hostname}
   :com.devereux-henley.rts-web.web.view/logout-view                      {:auth-hostname auth-hostname}
   :com.devereux-henley.rts-web.web.view/game-context-middleware            default-api-dependencies
   :com.devereux-henley.rts-web.web.view/faction-view                     default-api-dependencies
   :com.devereux-henley.rts-web.web.view/unit-view                        default-api-dependencies
   :com.devereux-henley.rts-web.web.view/draft-view                       default-api-dependencies
   :com.devereux-henley.rts-web.web.draft/draft-unit-panel                 default-api-dependencies
   :com.devereux-henley.rts-web.web.draft/draft-add-unit                  default-api-dependencies
   :com.devereux-henley.rts-web.web.draft/draft-remove-unit               default-api-dependencies
   :com.devereux-henley.rts-web.web.view/my-drafts-view                   default-api-dependencies
   :com.devereux-henley.rts-web.web.view/game-index-view                  default-view-dependencies
   :com.devereux-henley.rts-web.web.view/create-draft-view                default-api-dependencies
   :com.devereux-henley.rts-web.web.game/get-game                         default-api-dependencies
   :com.devereux-henley.rts-web.web.game/get-games                        default-api-dependencies
   :com.devereux-henley.rts-web.web.game/get-faction                      default-api-dependencies
   :com.devereux-henley.rts-web.web.game/get-game-social-link             default-api-dependencies
   :com.devereux-henley.rts-web.web.social-media/get-platform             default-api-dependencies
   :com.devereux-henley.rts-web.web.tournament/get-tournament             default-api-dependencies
   :com.devereux-henley.rts-web.web.tournament/get-tournament-snapshot    default-api-dependencies
   :com.devereux-henley.rts-web.web.tournament/get-tournaments            default-api-dependencies
   :com.devereux-henley.rts-web.web.tournament/create-tournament          default-api-dependencies
   :com.devereux-henley.rts-web.web.game/create-draft                    default-api-dependencies
   :com.devereux-henley.rts-web.web.routes/routes
   [rts-web/root-route
    rts-web/icon-routes
    rts-web/view-routes
    rts-web/api-routes]
   ::web/app                                                               {:routes        (integrant.core/ref :com.devereux-henley.rts-web.web.routes/routes)
                                                                            :session-name  session-name
                                                                            :auth-hostname auth-hostname}
   ::web/service                                                           {:handler       (integrant.core/ref ::web/app)
                                                                            :configuration {:port port, :join? false}}})
