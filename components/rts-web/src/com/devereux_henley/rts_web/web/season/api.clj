(ns com.devereux-henley.rts-web.web.season.api
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]
   [reitit.core]))

(defn get-season-by-eid
  [dependencies eid]
  (or (domain/get-season-by-eid dependencies eid)
      {:type :missing/resource :name "season" :id eid}))

(defn get-seasons-for-league
  [dependencies league-eid {:keys [hostname router]}]
  {:type      :collection/season
   :_embedded {:results (if league-eid
                          (domain/get-seasons-for-league dependencies league-eid)
                          (domain/get-seasons dependencies))}
   :_links    {:self (str hostname
                          (-> router
                              (reitit.core/match-by-name! :collection/season)
                              (reitit.core/match->path
                               (when league-eid {:league-eid league-eid}))))}})

(defmethod integrant.core/init-key ::get-season
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    (let [result (get-season-by-eid dependencies eid)]
      (if (= :missing/resource (:type result))
        {:status 404 :body result}
        {:status 200 :body result}))))

(defmethod integrant.core/init-key ::get-seasons-for-league
  [_init-key dependencies]
  (fn [{{{:keys [league-eid]} :query} :parameters
        router                        :reitit.core/router
        :as                           _request}]
    {:status 200
     :body   (get-seasons-for-league dependencies league-eid
                                     {:hostname (:hostname dependencies) :router router})}))

(defmethod integrant.core/init-key ::create-season
  [_init-key dependencies]
  (fn [{{{:keys [name timezone start-at end-at]} :body
         {:keys [eid league-eid game-eid]}       :path} :parameters
        router                                          :reitit.core/router
        session                                         :ory-session
        :as                                             _request}]
    (let [user-sub (get-in session [:identity :id])
          result   (domain/create-season
                    dependencies
                    {:eid        eid
                     :league-eid league-eid
                     :name       name
                     :timezone   timezone
                     :start-at   start-at
                     :end-at     end-at}
                    user-sub)]
      (if (= :season/error (:type result))
        {:status 422 :body result}
        (let [response (web.core/handle-create-response
                        domain/season-resource
                        {:hostname (:hostname dependencies) :router router}
                        (constantly result))]
          (assoc-in response [:headers "HX-Redirect"]
                    (str "/view/game/" game-eid
                         "/league/" league-eid "/season/" eid "/index.html")))))))
