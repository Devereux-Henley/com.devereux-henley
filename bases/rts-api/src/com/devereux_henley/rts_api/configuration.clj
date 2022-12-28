(ns com.devereux-henley.rts-api.configuration
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [com.devereux-henley.rts-api.db :as db]
   [com.devereux-henley.rts-api.schema :as schema]
   [com.devereux-henley.rts-api.web :as web]
   [com.devereux-henley.rts-api.web.asset :as web.asset]
   [com.devereux-henley.rts-api.web.game :as web.game]
   [com.devereux-henley.rts-api.web.social-media :as web.social-media]
   [com.devereux-henley.rts-api.web.routes :as routes]
   [integrant.core]
   [com.devereux-henley.rts-api.web.view :as web.view]))

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
   ::web/routes
   [["/"
     {:get {:no-doc   true
            :produces ["text/html"]
            :handler  (fn [_request] {:status 301 :headers {"Location" "/view/dashboard.html"}})}}]
    ["/icon/social-media/:eid"
     {:get {:no-doc true
            :parameters {:path schema.contract/id-path-parameter}
            :produces ["image/svg+xml"]
            :handler web.asset/icon-handler}}]
    routes/view-routes
    routes/api-routes]
   ::web/app     {:routes (integrant.core/ref ::web/routes)}
   ::web/service {:handler       (integrant.core/ref ::web/app)
                  :configuration {:port 3001, :join? false}}
   })
