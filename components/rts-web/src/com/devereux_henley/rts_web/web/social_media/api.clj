(ns com.devereux-henley.rts-web.web.social-media.api
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]))

(defn get-platform-by-eid
  [dependencies eid]
  (or (domain/get-platform-by-eid dependencies eid)
      {:type :missing/resource :name "platform" :id eid}))

(defmethod integrant.core/init-key ::get-platform
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        :as                   _request}]
    (let [result (get-platform-by-eid dependencies eid)]
      (if (= :missing/resource (:type result))
        {:status 404 :body result}
        {:status 200 :body result}))))
