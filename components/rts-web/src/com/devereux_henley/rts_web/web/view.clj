(ns com.devereux-henley.rts-web.web.view
  "Shared template-rendering infrastructure (base context, standard handlers,
  Selmer filters, game-context middleware) plus the unparented, non-domain
  pages (about / contact / login / logout / game-selector / game-view).

  Domain-specific view handlers live in `web/<domain>/view.clj`."
  (:require
   [clojure.string :as string]
   [com.devereux-henley.rts-data-access.contract :as db]
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

(defn render-entity-view
  "Render `template-name` with the domain view-model merged onto the base
   context, or a 404 when the model is a `:missing/resource` marker.

   The view-model is the complete template context produced by a domain
   `<view>-view-model` function: the primary entity under `:data` plus any
   lists, flags, and labels the template reads at the top level. Callers
   merge genuinely view-only request-derived extras (filesystem asset paths)
   onto the model before passing it in."
  [request template-name view-model]
  (if (= :missing/resource (:type view-model))
    {:status 404 :body view-model}
    {:status 200
     :body   (render/render-view template-name
                                 (merge (base-context request) view-model))}))

(defmethod integrant.core/init-key ::game-context-middleware
  [_init-key {:keys [datalog-connection]}]
  (fn [handler]
    (fn [request]
      (let [game-eid (get-in request [:parameters :path :game-eid])
            game     (db/game-by-eid datalog-connection game-eid)]
        (if game
          (handler (assoc request :game-context {:game-eid game-eid
                                                 :game     (assoc game :logo (skin/logo-for-game game-eid))
                                                 :factions (db/factions-for-game datalog-connection game-eid)
                                                 :socials  (db/socials-for-game datalog-connection game-eid)
                                                 :skin     (skin/skin-for-game game-eid)}))
          {:status 404 :body {:type :missing/resource :name "game" :id game-eid}})))))

(defmethod integrant.core/init-key ::game-selector-view
  [_init-key {:keys [datalog-connection]}]
  (fn [request]
    (let [games (mapv (fn [game]
                        (assoc game :logo (skin/logo-for-game (:eid game))))
                      (db/games datalog-connection))]
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
