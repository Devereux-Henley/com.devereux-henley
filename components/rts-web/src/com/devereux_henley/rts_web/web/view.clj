(ns com.devereux-henley.rts-web.web.view
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [clojure.string :as str]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.web.game :as web.game]
   [integrant.core]
   [jsonista.core :as jsonista]
   [selmer.parser]
   [taoensso.timbre :as log]))

(defn ^:private error-page
  [status status-text message]
  {:status  status
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (selmer.parser/render-file
             "rts-api/view/error.html"
             {:status-text status-text
              :message     message})})

(defn standard-view-handler
  [view-name request]
  (try
    {:status 200
     :body   (selmer.parser/render-file
              (str "rts-api/view/" view-name)
              (merge {:session (:ory-session request)}
                     (:game-context request)))}
    (catch Exception exc
      (log/error exc)
      (error-page 500 "Internal Server Error" "An unexpected error occurred."))))

(defn standard-entity-view-handler
  [pipeline-fn template-name extra-data-fn request]
  (let [{{{:keys [eid]} :path} :parameters} request]
    (try
      (let [result (pipeline-fn eid)]
        (either/branch
         result
         (fn [_] (error-page 404 "Not Found" "The requested resource could not be found."))
         (fn [data]
           {:status 200
            :body   (selmer.parser/render-file
                     (str "rts-api/view/" template-name)
                     (merge {:data    data
                             :session (:ory-session request)}
                            (:game-context request)
                            (extra-data-fn data request)))})))
      (catch Exception exc
        (log/error exc)
        (error-page 500 "Internal Server Error" "An unexpected error occurred.")))))

(defmethod integrant.core/init-key ::game-context-middleware
  [_init-key dependencies]
  (fn [handler]
    (fn [request]
      (let [game-eid (get-in request [:parameters :path :game-eid])]
        (if-let [game (domain/get-game-by-eid dependencies game-eid)]
          (handler (assoc request :game-context {:game-eid game-eid
                                                 :game     game
                                                 :factions (domain/get-factions-for-game dependencies game-eid)
                                                 :socials  (domain/get-socials-for-game dependencies game-eid)}))
          (error-page 404 "Not Found" "The requested game could not be found."))))))

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
              (partial web.game/load-units-by-category-for-faction dependencies)))
           "faction.html"
           (fn [_data _request] {})))

(defn parse-unit-statistics
  [unit-statistics-str]
  (try
    (let [stats (jsonista/read-value unit-statistics-str (jsonista/object-mapper {:decode-key-fn name}))]
      (into []
            (keep (fn [[k v]]
                    (cond
                      (and (vector? v) (empty? v)) nil
                      (= v 0)                      nil
                      (vector? v)                  {:stat (str/replace k "_" " ") :value (str/join ", " v)}
                      :else                        {:stat (str/replace k "_" " ") :value v})))
            stats))
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
           (fn [data _request] {:unit-statistics (parse-unit-statistics (:unit-statistics data))})))

(defmethod integrant.core/init-key ::draft-view
  [_init-key dependencies]
  (partial standard-entity-view-handler
           (fn [eid]
             (cats/>>=
              (either/right eid)
              (partial web.game/get-draft-by-eid dependencies)))
           "draft-index.html"
           (fn [draft request]
             {:faction (domain/get-faction-by-eid dependencies (:faction-eid draft))
              :game    (:game (:game-context request))})))

(defmethod integrant.core/init-key ::my-drafts-view
  [_init-key dependencies]
  (fn [{session      :ory-session
       game-context :game-context
       :as          _request}]
    (try
      (let [player-sub (get-in session [:identity :id])]
        {:status 200
         :body   (selmer.parser/render-file
                  "rts-api/view/my-drafts.html"
                  (merge {:drafts  (domain/get-drafts-for-player-by-game dependencies player-sub (:game-eid game-context))
                          :session session}
                         game-context))})
      (catch Exception exc
        (log/error exc)
        (error-page 500 "Internal Server Error" "An unexpected error occurred.")))))

(defmethod integrant.core/init-key ::game-index-view
  [_init-key _dependencies]
  (fn [{game-context :game-context
       session      :ory-session
       :as          _request}]
    (try
      {:status 200
       :body   (selmer.parser/render-file
                "rts-api/view/game-index.html"
                (merge {:data    (:game game-context)
                        :session session}
                       game-context))}
      (catch Exception exc
        (log/error exc)
        (error-page 500 "Internal Server Error" "An unexpected error occurred.")))))

(defmethod integrant.core/init-key ::create-draft-view
  [_init-key dependencies]
  (fn [{game-context :game-context
       session      :ory-session
       :as          _request}]
    (try
      (let [game-modes (domain/get-game-modes-for-game dependencies (:game-eid game-context))]
        {:status 200
         :body   (selmer.parser/render-file
                  "rts-api/view/create-draft.html"
                  (merge {:game-modes game-modes
                          :session    session}
                         game-context))})
      (catch Exception exc
        (log/error exc)
        (error-page 500 "Internal Server Error" "An unexpected error occurred.")))))

(defmethod integrant.core/init-key ::logout-view
  [_init-key {:keys [auth-hostname]}]
  (fn [request]
    (log/info (:ory-session request))
    {:status 301 :headers {"Location" (str auth-hostname "/self-service/logout?token=")}}))
