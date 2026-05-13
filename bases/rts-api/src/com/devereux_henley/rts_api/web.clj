(ns com.devereux-henley.rts-api.web
  (:require
   [clj-http.client :as client]
   [clojure.string :as str]
   [com.devereux-henley.content-negotiation.contract :as content-negotiation]
   [com.devereux-henley.resourcekit.contract :as resourcekit]
   [com.devereux-henley.rts-api.extensions.clj-http] ;; Patches multimethod for clj-http
   [com.devereux-henley.rts-api.method-override :as method-override]
   [com.devereux-henley.rts-api.produces-enforcement :as produces-enforcement]
   [com.devereux-henley.rts-web.contract :as rts-web]
   [integrant.core]
   [malli.util]
   [muuntaja.core :as m]
   [muuntaja.format.form]
   [reitit.coercion :as coercion-error]
   [reitit.coercion.malli]
   [reitit.dev.pretty :as pretty]
   [reitit.ring :as ring]
   [reitit.ring.coercion :as coercion]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [ring.adapter.jetty :as jetty]
   [ring.middleware.cookies]
   [taoensso.timbre :as log])
  (:import
   [java.net ConnectException]))

(defn view-by-type
  [type]
  (get
   {:game/game                "rts-web/resource/game.html"
    :game/faction             "rts-web/resource/faction.html"
    :game/draft               "rts-web/resource/draft.html"
    :game/social-link         "rts-web/resource/game-social-link.html"
    :collection/game          "rts-web/resource/game-collection.html"
    :draft/unit               "rts-web/resource/draft-unit.html"
    :draft/entry              "rts-web/resource/draft-entry.html"
    :draft/add-success        "rts-web/resource/draft-add-success.html"
    :draft/add-error          "rts-web/resource/draft-add-error.html"
    :draft/update-success     "rts-web/resource/draft-update-success.html"
    :draft/update-error       "rts-web/resource/draft-add-error.html"
    :draft/remove-success     "rts-web/resource/draft-remove-success.html"
    :tournament/tournament    "rts-web/resource/tournament.html"
    :collection/tournament    "rts-web/resource/tournament-collection.html"
    :tournament/phase         "rts-web/resource/tournament-phase.html"
    :tournament/round         "rts-web/resource/tournament-round.html"
    :tournament/entry         "rts-web/resource/tournament-entry.html"
    :tournament/entry-deleted "rts-web/resource/tournament-entry-deleted.html"
    :tournament/entry-error   "rts-web/resource/tournament-entry-error.html"
    :tournament/entries       "rts-web/resource/tournament-entries.html"
    :tournament/status        "rts-web/resource/tournament-status.html"
    :tournament/registration  "rts-web/resource/tournament-registration.html"
    :tournament/match         "rts-web/resource/tournament-match.html"
    :tournament/matches       "rts-web/resource/tournament-matches.html"
    :tournament/games         "rts-web/resource/tournament-games.html"
    :league/league            "rts-web/resource/league.html"
    :collection/league        "rts-web/resource/league-collection.html"
    :season/season            "rts-web/resource/season.html"
    :collection/season        "rts-web/resource/season-collection.html"
    :stats/faction-standings  "rts-web/resource/stats-faction-standings.html"
    :social-media/platform    "rts-web/resource/social-media.html"
    :missing/resource         "rts-web/resource/missing.html"
    "exception"               "rts-web/resource/error.html"}
   type
   "rts-web/resource/unknown.html"))

(def continuity-key "ory_kratos_continuity")

(defn try-get-session
  [url cookies]
  (try
    (let [{:keys [status body]} (client/get (str url "/sessions/whoami")
                                            {:as      :jsonista
                                             :cookies cookies})]
      (case status
        200 body
        401 nil
        (do
          (log/error (str "Unexpected status code " status))
          nil)))
    (catch ConnectException exc
      (log/error "Could not connect to authentication/authorization service." exc)
      nil)))

(defn ^:private asset-path?
  [uri]
  (some #(str/starts-with? uri %)
        [(str resourcekit/asset-path "/")
         "/style/"
         "/image/"
         "/icon/"
         "/card/"]))

(defn ^:private accepts-html?
  [request]
  (let [accept (get-in request [:headers "accept"] "")]
    (or (str/includes? accept "text/html")
        (str/includes? accept "htmx+html"))))

(defn ^:private render-error-page
  [status status-text message request]
  {:status  status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (rts-web/render-view "error.html"
                                 {:status-text status-text
                                  :message     message
                                  :session     (:ory-session request)})})

(defn ^:private render-error-fragment
  [status message]
  {:status  status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (str "<section class=\"resource\" role=\"alert\" aria-labelledby=\"error-heading\">"
                 "<p id=\"error-heading\">" message "</p>"
                 "</section>")})

(defn ^:private html-error-response
  [status status-text message request]
  (if (get-in request [:headers "hx-request"])
    (render-error-fragment status message)
    (render-error-page status status-text message request)))

(defn ^:private error-response
  [status status-text message request]
  (if (accepts-html? request)
    (html-error-response status status-text message request)
    {:status status :body {:error message}}))

(def ^:private exception-handlers
  (merge
   exception/default-handlers
   {::coercion-error/request-coercion
    (fn [exc request]
      (log/warn "Request coercion failure" {:uri (:uri request) :message (ex-message exc)})
      (error-response 400 "Bad Request"
                      "The request could not be processed. Please check your input and try again."
                      request))

    ::coercion-error/response-coercion
    (fn [exc request]
      (log/error exc "Response coercion failure" {:uri (:uri request)})
      (error-response 500 "Internal Server Error"
                      "An unexpected error occurred while processing the response."
                      request))

    ConnectException
    (fn [exc request]
      (log/error exc "Service unavailable" {:uri (:uri request)})
      (error-response 503 "Service Unavailable"
                      "A required service is temporarily unavailable. Please try again shortly."
                      request))

    clojure.lang.ExceptionInfo
    (fn [exc request]
      (let [status (case (:error/kind (ex-data exc))
                     :error/missing  404
                     :error/invalid  400
                     :error/conflict 409
                     500)]
        (when (= status 500) (log/error exc "Application error" {:uri (:uri request)}))
        (error-response status
                        (case status
                          400 "Bad Request"
                          404 "Not Found"
                          409 "Conflict"
                          "Internal Server Error")
                        (ex-message exc)
                        request)))

    ::exception/default
    (fn [exc request]
      (log/error exc "Unhandled exception" {:uri (:uri request)})
      (error-response 500 "Internal Server Error"
                      "An unexpected error occurred."
                      request))}))

(defn ory-session-middleware
  [ory-base-url session-name handler]
  (fn [request]
    (if (asset-path? (:uri request))
      (handler request)
      (let [{:keys [cookies]} request
            session           (get-in cookies [session-name :value])
            continuity        (get-in cookies [continuity-key :value])]
        (handler
         (if (and session continuity)
           (assoc request :ory-session (try-get-session
                                        ory-base-url
                                        {session-name   {:value session}
                                         continuity-key {:value continuity}}))
           request))))))

(defmethod integrant.core/init-key ::ory-auth-middleware
  [_init-key {:keys [auth-hostname session-name]}]
  (fn [handler]
    (ory-session-middleware auth-hostname session-name handler)))

(defmethod integrant.core/init-key ::app
  [_init-key {:keys [routes auth-middleware]}]
  (ring/ring-handler
   (ring/router
    routes
    {;;:reitit.middleware/transform dev/print-request-diffs ;; pretty diffs
     :exception pretty/exception
     :data      {:coercion   (reitit.coercion.malli/create
                              {;; set of keys to include in error messages
                               :error-keys       #{#_:type
                                                   :coercion
                                                   :in
                                                   :schema
                                                   :value
                                                   :errors
                                                   :humanized
                                                   #_:transformed}
                               :compile          malli.util/closed-schema
                               :strip-extra-keys true
                               :default-values   true
                               :options          nil})
                 :muuntaja   (m/create
                              (-> m/default-options
                                  (assoc :return :bytes
                                         :default-format "text/html")
                                  (update :formats dissoc "application/edn"
                                          "application/transit+json" "application/transit+msgpack")
                                  (assoc-in
                                   [:formats "application/x-www-form-urlencoded"]
                                   muuntaja.format.form/format)
                                  (assoc-in [:formats "text/html"]
                                            content-negotiation/html-format)
                                  (assoc-in [:formats "text/html" :encoder-opts] {:view-fn view-by-type})
                                  (assoc-in [:formats "application/htmx+html"]
                                            content-negotiation/html-htmx-format)
                                  (assoc-in [:formats "application/htmx+html" :encoder-opts] {:view-fn view-by-type})))
                 :middleware [;; query-params & form-params
                              parameters/parameters-middleware
                              ;; reject Accept that doesn't match the matched
                              ;; route's :produces before muuntaja sees it
                              produces-enforcement/wrap-enforce-produces
                              ;; content-negotiation
                              muuntaja/format-negotiate-middleware
                              ;; encoding response body
                              muuntaja/format-response-middleware
                              ;; exception handling
                              (exception/create-exception-middleware exception-handlers)
                              ;; decoding request body
                              muuntaja/format-request-middleware
                              ;; coercing response bodys
                              coercion/coerce-response-middleware
                              ;; coercing request parameters
                              coercion/coerce-request-middleware
                              ;; multipart
                              multipart/multipart-middleware]}})
   (ring/routes
    (ring/create-resource-handler {:root resourcekit/asset-root
                                   :path resourcekit/asset-path})
    (ring/create-resource-handler {:root "rts-web/asset"
                                   :path "/"
                                   :not-found-handler
                                   (fn [_] {:status 404
                                            :body   "Oopsie"})})
    (ring/create-default-handler))
   {:middleware [ring.middleware.cookies/wrap-cookies
                 method-override/wrap-method-override
                 auth-middleware]}))

(defmethod integrant.core/init-key ::service
  [_init-key {:keys [handler configuration]}]
  (jetty/run-jetty handler configuration))

(defmethod integrant.core/halt-key! ::service
  [_init-key service]
  (.stop service))
