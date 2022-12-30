(ns com.devereux-henley.rts-api.configuration
  (:require
   [com.devereux-henley.rts-api.db :as db]
   [com.devereux-henley.rts-api.web :as web]
   [com.devereux-henley.rts-api.web.game :as web.game]
   [com.devereux-henley.rts-api.web.social-media :as web.social-media]
   [com.devereux-henley.rts-api.web.routes :as web.routes]
   [com.devereux-henley.rts-api.web.view :as web.view]
   [integrant.core]))

(def port
  (if-let [port-property (System/getenv "RTS_API_PORT")]
    (Integer/parseInt port-property) ;; If this cannot be parsed, let it fail during startup.
    3001))

(def hostname (or (System/getenv "RTS_API_HOSTNAME") "http://localhost:3001"))
(def auth-hostname (or (System/getenv "AUTH_HOSTNAME") "http://localhost:4000"))
(def session-name (str "ory_session_" (or (System/getenv "AUTH_SLUG") "eloquentyalowwhtijq6my4")))
(def core-configuration
  {::db/connection       {}
   ::web/swagger-handler {}
   ::web.view/dashboard-view {:session-name session-name}
   ::web.view/game-view {:session-name session-name}
   ::web.view/about-view {:session-name session-name}
   ::web.view/contact-view {:session-name session-name}
   ::web.view/login-view {:auth-hostname auth-hostname}
   ::web.view/logout-view {:auth-hostname auth-hostname}
   ::web.game/get-game   {:hostname hostname
                          :connection (integrant.core/ref ::db/connection)}
   ::web.game/get-games  {:hostname hostname
                          :connection (integrant.core/ref ::db/connection)}
   ::web.game/get-game-social-link {:hostname hostname
                                    :connection (integrant.core/ref ::db/connection)}
   ::web.social-media/get-platform {:hostname hostname
                                    :connection (integrant.core/ref ::db/connection)}
   ::web.routes/routes
   [web.routes/root-route
    web.routes/icon-routes
    web.routes/view-routes
    web.routes/api-routes]
   ::web/app     {:routes (integrant.core/ref ::web.routes/routes)}
   ::web/service {:handler       (integrant.core/ref ::web/app)
                  :configuration {:port port, :join? false}}
   })
