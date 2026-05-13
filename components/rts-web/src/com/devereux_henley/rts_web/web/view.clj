(ns com.devereux-henley.rts-web.web.view
  "Shared template-rendering infrastructure (base context, standard handlers,
  Selmer filters, game-context middleware) plus the unparented, non-domain
  pages (about / contact / login / logout / game-selector / game-view).

  Domain-specific view handlers live in `web/<domain>/view.clj`."
  (:require
   [clojure.string :as string]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.skin :as skin]
   [integrant.core]
   [selmer.filters]
   [taoensso.timbre :as log]))

(defn- active-nav
  "Derive the navbar section label for the given request URI."
  [uri]
  (cond
    (nil? uri)                            nil
    (string/includes? uri "/draft")       "drafts"
    (string/includes? uri "/competitive") "competitive"
    (string/includes? uri "/league")      "competitive"
    (string/includes? uri "/season")      "competitive"
    (string/includes? uri "/tournament")  "competitive"
    (string/includes? uri "/faction")     "atlas"
    :else                                 nil))

(defn base-context
  "Template context shared across every view — session, active navbar section,
   any game-context assembled by the middleware, and the HX-Trigger
   registry templates use to wire fragment refresh.

   The trigger registry is injected onto the request by
   `orchestration/middleware`; this function just surfaces it under
   `:triggers` for Selmer (`{{ triggers.<event-id> }}`)."
  [request]
  (-> {:session    (:ory-session request)
       :active-nav (active-nav (:uri request))
       :triggers   (:web-triggers request)}
      (merge (:game-context request))))

(selmer.filters/add-filter! :not-empty? (comp boolean seq))

(selmer.filters/add-filter! :url-encode
                            (fn [s]
                              (java.net.URLEncoder/encode (str s) "UTF-8")))

(defn standard-view-handler
  [view-name request]
  {:status 200
   :body   (render/render-view view-name (base-context request))})

(defn standard-entity-view-handler
  [pipeline-fn template-name extra-data-fn request]
  (let [{{{:keys [eid]} :path} :parameters} request
        data                                (pipeline-fn eid)]
    (if (= :missing/resource (:type data))
      {:status 404 :body data}
      {:status 200
       :body   (render/render-view template-name
                                   (merge (base-context request)
                                          {:data data}
                                          (extra-data-fn data request)))})))

(defmethod integrant.core/init-key ::game-context-middleware
  [_init-key dependencies]
  (fn [handler]
    (fn [request]
      (let [game-eid (get-in request [:parameters :path :game-eid])
            game     (domain/get-game-by-eid dependencies game-eid)]
        (if game
          (handler (assoc request :game-context {:game-eid game-eid
                                                 :game     (assoc game :logo (skin/logo-for-game game-eid))
                                                 :factions (domain/get-factions-for-game dependencies game-eid)
                                                 :socials  (domain/get-socials-for-game dependencies game-eid)
                                                 :skin     (skin/skin-for-game game-eid)}))
          {:status 404 :body {:type :missing/resource :name "game" :id game-eid}})))))

(defmethod integrant.core/init-key ::game-selector-view
  [_init-key dependencies]
  (fn [request]
    (let [games (mapv (fn [game]
                        (assoc game :logo (skin/logo-for-game (:eid game))))
                      (domain/get-games dependencies))]
      {:status 200
       :body   (render/render-view "game-selector.html"
                                   (assoc (base-context request) :games games))})))

(defmethod integrant.core/init-key ::game-view
  [_init-key _dependencies]
  (partial standard-view-handler "game.html"))

(defmethod integrant.core/init-key ::about-view
  [_init-key _dependencies]
  (partial standard-view-handler "about.html"))

(defmethod integrant.core/init-key ::contact-view
  [_init-key _dependencies]
  (partial standard-view-handler "contact.html"))

(defmethod integrant.core/init-key ::login-view
  [_init-key {:keys [auth-hostname]}]
  (fn [_request] {:status 301 :headers {"Location" (str auth-hostname "/ui/login")}}))

(defmethod integrant.core/init-key ::logout-view
  [_init-key {:keys [auth-hostname]}]
  (fn [request]
    (log/info (:ory-session request))
    {:status 301 :headers {"Location" (str auth-hostname "/self-service/logout?token=")}}))
