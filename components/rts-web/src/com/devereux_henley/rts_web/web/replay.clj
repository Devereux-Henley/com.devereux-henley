(ns com.devereux-henley.rts-web.web.replay
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]))

(defn- fetch-replay-or-missing
  [dependencies eid]
  (if-let [replay (domain/get-replay-by-eid dependencies eid)]
    replay
    {:type :missing/resource :name "replay" :id eid}))

(defmethod integrant.core/init-key ::get-replay
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        router                :reitit.core/router
        :as                   _request}]
    (web.core/handle-fetch-response
     domain/replay-resource
     {:hostname (:hostname dependencies) :router router}
     #(fetch-replay-or-missing dependencies eid))))

(defmethod integrant.core/init-key ::create-replay
  [_init-key dependencies]
  (fn [{{{:keys [eid]}              :path
         {{:keys [tempfile]} :file} :multipart} :parameters
        session                                 :ory-session
        router                                  :reitit.core/router
        :as                                     _request}]
    (if (nil? tempfile)
      {:status 400
       :body   {:type    :replay/upload-error
                :message "missing file upload (form field name must be 'file')"}}
      (let [response (web.core/handle-create-response
                      domain/replay-resource
                      {:hostname (:hostname dependencies) :router router}
                      #(domain/create-replay
                        dependencies
                        {:eid             eid
                         :file-path       (.getAbsolutePath tempfile)
                         :uploaded-by-sub (get-in session [:identity :id])}))]
        (assoc-in response [:headers "HX-Redirect"] (str "/view/replay/" eid "/index.html"))))))

(defmethod integrant.core/init-key ::declare-winner
  [_init-key dependencies]
  (fn [{{{:keys [eid]}                  :path
         {:keys [winning-alliance-idx]} :body} :parameters
        router                                 :reitit.core/router
        :as                                    _request}]
    (web.core/handle-fetch-response
     domain/replay-resource
     {:hostname (:hostname dependencies) :router router}
     #(domain/declare-winner dependencies eid winning-alliance-idx))))
