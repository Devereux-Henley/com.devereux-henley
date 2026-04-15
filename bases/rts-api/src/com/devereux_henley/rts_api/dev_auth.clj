(ns com.devereux-henley.rts-api.dev-auth
  "Dev-only authentication profile. Stubs Ory with a simple cookie-session
   impersonation layer so local development and automated tests can \"log in\"
   as any predefined user without running the Ory stack. The request shape
   (`:ory-session`) and nested user data match what Ory's `/sessions/whoami`
   returns, so downstream handlers, templates, and domain code do not care
   which profile is active."
  (:require
   [clojure.string :as str]
   [com.devereux-henley.resourcekit.contract :as resourcekit]
   [integrant.core]))

(def cookie-name
  "Name of the cookie that stores the impersonated user's id."
  "dev_impersonation")

(def dev-users
  "Predefined dev users keyed by id. Shape mirrors an Ory /sessions/whoami
   response so downstream code can read `[:identity :id]` and
   `[:identity :traits :first_name]` unchanged."
  {"dev-admin"
   {:active   true
    :identity {:id     "dev-admin"
               :traits {:first_name "Dev"
                        :last_name  "Admin"
                        :email      "dev-admin@example.com"}}}
   "dev-player-one"
   {:active   true
    :identity {:id     "dev-player-one"
               :traits {:first_name "Player"
                        :last_name  "One"
                        :email      "player-one@example.com"}}}
   "dev-player-two"
   {:active   true
    :identity {:id     "dev-player-two"
               :traits {:first_name "Player"
                        :last_name  "Two"
                        :email      "player-two@example.com"}}}})

(defn- asset-path?
  [uri]
  (some #(str/starts-with? uri %)
        [(str resourcekit/asset-path "/")
         "/style/"
         "/image/"
         "/icon/"
         "/card/"]))

(def default-user-id
  "User id attached to every request when the impersonation cookie is
   missing. Ensures the dev profile is always \"logged in\" so templates
   never render the Ory login button."
  "dev-admin")

(defn dev-session-middleware
  "Replacement for `web/ory-session-middleware`. Looks up the impersonation
   cookie first; if absent (or pointing at an unknown user) it falls back to
   `default-user-id` so every request sees a populated `:ory-session`."
  [users default-id handler]
  (fn [request]
    (if (asset-path? (:uri request))
      (handler request)
      (let [cookie-user-id (get-in request [:cookies cookie-name :value])
            session        (or (get users cookie-user-id)
                               (get users default-id))]
        (handler (assoc request :ory-session session))))))

(defn- impersonate-cookie
  [user-id]
  {:value     user-id
   :path      "/"
   :http-only true
   :same-site :lax})

(def ^:private clear-cookie
  {:value     ""
   :path      "/"
   :max-age   0
   :http-only true
   :same-site :lax})

(defmethod integrant.core/init-key ::impersonation-middleware
  [_init-key {:keys [users default-user-id]}]
  (fn [handler]
    (dev-session-middleware users default-user-id handler)))

(defmethod integrant.core/init-key ::users
  [_init-key _dependencies]
  dev-users)

(defmethod integrant.core/init-key ::impersonate-handler
  [_init-key {:keys [users]}]
  (fn [{{{:keys [user-id]} :path} :parameters :as _request}]
    (if (contains? users user-id)
      {:status  303
       :headers {"Location" "/view/dashboard.html"}
       :cookies {cookie-name (impersonate-cookie user-id)}
       :body    ""}
      {:status 404
       :body   {:error (str "Unknown dev user: " user-id)
                :known-users (vec (keys users))}})))

(defmethod integrant.core/init-key ::logout-handler
  [_init-key _dependencies]
  (fn [_request]
    {:status  303
     :headers {"Location" "/view/dashboard.html"}
     :cookies {cookie-name clear-cookie}
     :body    ""}))

(defmethod integrant.core/init-key ::list-users-handler
  [_init-key {:keys [users]}]
  (fn [_request]
    {:status 200
     :body   {:users (mapv (fn [[id user]]
                             {:id           id
                              :display-name (str (get-in user [:identity :traits :first_name])
                                                 " "
                                                 (get-in user [:identity :traits :last_name]))
                              :email        (get-in user [:identity :traits :email])})
                           users)}}))

(def routes
  "Reitit route tree exposing dev-only impersonation endpoints. Added to the
   router only under the development profile."
  ["/dev"
   {:no-doc true}
   ["/users"
    {:get {:produces ["application/json"]
           :handler  (integrant.core/ref ::list-users-handler)}}]
   ["/impersonate/:user-id"
    {:post {:parameters {:path [:map [:user-id :string]]}
            :handler    (integrant.core/ref ::impersonate-handler)}
     :get  {:parameters {:path [:map [:user-id :string]]}
            :handler    (integrant.core/ref ::impersonate-handler)}}]
   ["/logout"
    {:post {:handler (integrant.core/ref ::logout-handler)}
     :get  {:handler (integrant.core/ref ::logout-handler)}}]])
