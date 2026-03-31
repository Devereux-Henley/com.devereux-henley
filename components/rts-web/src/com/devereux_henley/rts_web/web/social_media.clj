(ns com.devereux-henley.rts-web.web.social-media
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.http.contract :as web.core]
   [com.devereux-henley.rts-domain.contract :as domain]
   [integrant.core]))

(def get-platform-by-eid
  (web.core/standard-fetch domain/get-platform-by-eid :social-media/platform))

(defmethod integrant.core/init-key ::get-platform
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       router                :reitit.core/router
       :as                   _request}]
    (web.core/handle-fetch-response
     domain/social-media-platform-resource
     {:router router :hostname (:hostname dependencies)}
     (cats/>>=
      (either/right eid)
      (partial get-platform-by-eid dependencies)))))
