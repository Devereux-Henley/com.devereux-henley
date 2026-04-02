(ns com.devereux-henley.rts-web.web.view
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.game :as web.game]
   [integrant.core]
   [jsonista.core :as jsonista]
   [selmer.parser]
   [taoensso.timbre :as log]))

(defonce css-version (atom (System/currentTimeMillis)))

(defn standard-view-handler
  [view-name request]
  (try
    {:status 200
     :body   (selmer.parser/render-file
              (str "rts-api/view/" view-name)
              {:session     (:ory-session request)
               :css-version @css-version})}
    (catch Exception exc
      (log/error exc)
      {:status 500
       :body "<div>Something went wrong</div>"})))

(defn standard-entity-view-handler
  [pipeline-fn template-name extra-data-fn request]
  (let [{{{:keys [eid]} :path} :parameters} request]
    (try
      (let [result (pipeline-fn eid)]
        (either/branch
         result
         (fn [_] {:status 404 :body "<div>Not found</div>"})
         (fn [data]
           {:status 200
            :body   (selmer.parser/render-file
                     (str "rts-api/view/" template-name)
                     (merge {:data        data
                             :session     (:ory-session request)
                             :css-version @css-version}
                            (extra-data-fn data)))})))
      (catch Exception exc
        (log/error exc)
        {:status 500 :body "<div>Something went wrong</div>"}))))

(defmethod integrant.core/init-key ::dashboard-view
  [_init-key _dependencies]
  (partial standard-view-handler "dashboard.html"))

(defmethod integrant.core/init-key ::tournament-view
  [_init-key _dependencies]
  (partial standard-view-handler "tournament.html"))

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

(defmethod integrant.core/init-key ::faction-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid]
             (cats/>>=
              (either/right eid)
              (partial web.game/get-faction-by-eid dependencies)
              (partial web.game/load-factions-for-faction-game dependencies)
              (partial web.game/load-units-by-category-for-faction dependencies)))
           "faction.html"
           (fn [data] {:game-eid (:game-eid data)
                       :factions (get-in data [:_embedded :factions])})))

(defn parse-unit-statistics
  [unit-statistics-str]
  (try
    (let [stats (jsonista/read-value unit-statistics-str (jsonista/object-mapper {:decode-key-fn name}))]
      (mapv (fn [[k v]] {:stat k :value v}) stats))
    (catch Exception _
      [])))

(defmethod integrant.core/init-key ::unit-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid]
             (cats/>>=
              (either/right eid)
              (partial web.game/get-unit-by-eid dependencies)))
           "unit.html"
           (fn [data] {:unit-statistics (parse-unit-statistics (:unit-statistics data))})))

(defmethod integrant.core/init-key ::draft-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid]
             (cats/>>=
              (either/right eid)
              (partial web.game/get-draft-by-eid dependencies)))
           "draft-index.html"
           (fn [draft]
             (let [faction  (domain/get-faction-by-eid dependencies (:faction-eid draft))
                   game     (domain/get-game-by-eid dependencies (:game-eid faction))
                   factions (domain/get-factions-for-game dependencies (:eid game))]
               {:faction  faction
                :game     game
                :game-eid (:eid game)
                :factions factions}))))

(defmethod integrant.core/init-key ::my-drafts-view
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       session               :ory-session
       :as _request}]
    (try
      (let [player-sub (get-in session [:identity :id])]
        {:status 200
         :body   (selmer.parser/render-file
                  "rts-api/view/my-drafts.html"
                  {:drafts      (domain/get-drafts-for-player-by-game dependencies player-sub eid)
                   :factions    (domain/get-factions-for-game dependencies eid)
                   :game-eid    eid
                   :session     session
                   :css-version @css-version})})
      (catch Exception exc
        (log/error exc)
        {:status 500 :body "<div>Something went wrong</div>"}))))

(defmethod integrant.core/init-key ::game-index-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid]
             (cats/>>=
              (either/right eid)
              (partial web.game/get-game-by-eid dependencies)
              (partial web.game/get-socials-for-game dependencies)
              (partial web.game/get-factions-for-game dependencies)))
           "game-index.html"
           (fn [data] {:game-eid (:eid data)
                       :factions (get-in data [:_embedded :factions])})))

(defmethod integrant.core/init-key ::create-draft-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid]
             (cats/>>=
              (either/right eid)
              (partial web.game/get-game-by-eid dependencies)
              (partial web.game/get-factions-for-game dependencies)
              (partial web.game/get-game-modes-for-game dependencies)))
           "create-draft.html"
           (fn [data] {:game-eid   (:eid data)
                       :factions   (get-in data [:_embedded :factions])
                       :game-modes (get-in data [:_embedded :game-modes])})))

(defmethod integrant.core/init-key ::logout-view
  [_init-key {:keys [auth-hostname]}]
  (fn [request]
    (log/info (:ory-session request))
    {:status 301 :headers {"Location" (str auth-hostname "/self-service/logout?token=")}}))
