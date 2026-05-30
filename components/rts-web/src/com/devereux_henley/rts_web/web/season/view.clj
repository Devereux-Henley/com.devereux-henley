(ns com.devereux-henley.rts-web.web.season.view
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [integrant.core]))

(defmethod integrant.core/init-key ::season-options-fragment-view
  [_init-key dependencies]
  (fn [request]
    (let [league-eid (get-in request [:parameters :query :league-eid])
          seasons    (when league-eid
                       (domain/get-seasons-for-league dependencies league-eid))]
      {:status  200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body    (render/render-component "season-options.html"
                                         (assoc (web.view/base-context request) :seasons seasons))})))

(defmethod integrant.core/init-key ::create-season-view
  [_init-key dependencies]
  (fn [request]
    (let [league-eid (get-in request [:parameters :path :league-eid])
          league     (domain/get-league-by-eid dependencies league-eid)]
      (if (nil? league)
        {:status 404 :body {:type :missing/resource :name "league" :id league-eid}}
        {:status 200
         :body   (render/render-view "create-season.html"
                                     (assoc (web.view/base-context request)
                                            :league league
                                            :league-eid league-eid
                                            :season-eid (random-uuid)
                                            :timezones domain/common-timezones
                                            :default-timezone domain/default-timezone))}))))

(defmethod integrant.core/init-key ::season-view
  [_init-key dependencies]
  (fn [request]
    (web.view/render-entity-view request "season-index.html"
                                 (domain/season-view-model dependencies (-> request :parameters :path :eid)))))
