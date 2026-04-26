(ns com.devereux-henley.rts-web.web.social-media.api
  (:require
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]))

(defn get-platform-by-eid
  [dependencies eid]
  (or (domain/get-platform-by-eid dependencies eid)
      {:type :missing/resource :name "platform" :id eid}))

(defmethod integrant.core/init-key ::get-platform
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
        router                :reitit.core/router
        :as                   _request}]
    (web.core/handle-fetch-response
     domain/social-media-platform-resource
     {:router router :hostname (:hostname dependencies)}
     #(get-platform-by-eid dependencies eid))))
