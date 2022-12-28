(ns com.devereux-henley.rts-api.web.view
  (:require
   [integrant.core]
   [selmer.parser]
   [taoensso.timbre :as log]))

(defn standard-view-handler
  [session-name view-name request]
  (try
    {:status 200
     :body   (selmer.parser/render-file
              (str "rts-api/view/" view-name)
              {:session (get-in request [:cookies session-name :value])})}
    (catch Exception exc
      (log/error exc)
      {:status 500
       :body "<div>Something went wrong</div>"})))

(defmethod integrant.core/init-key ::dashboard-view
  [_init-key {:keys [session-name]}]
  (partial standard-view-handler session-name "dashboard.html"))

(defmethod integrant.core/init-key ::game-view
  [_init-key {:keys [session-name]}]
  (partial standard-view-handler session-name "game.html"))

(defmethod integrant.core/init-key ::about-view
  [_init-key {:keys [session-name]}]
  (partial standard-view-handler session-name "about.html"))

(defmethod integrant.core/init-key ::contact-view
  [_init-key {:keys [session-name]}]
  (partial standard-view-handler session-name "contact.html"))

(defmethod integrant.core/init-key ::login-view
  [_init-key {:keys [auth-hostname]}]
  (fn [_request] {:status 301 :headers {"Location" (str auth-hostname "/ui/login")}}))

(defmethod integrant.core/init-key ::logout-view
  [_init-key {:keys [auth-hostname]}]
  (fn [_request] {:status 301 :headers {"Location" (str auth-hostname "/self-service/logout")}}))
