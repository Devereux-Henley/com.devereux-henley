(ns com.devereux-henley.rts-api.web.social-media
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.rts-api.schema :as schema]
   [com.devereux-henley.rts-api.handlers.social-media :as handlers.social-media]
   [com.devereux-henley.rts-api.web.core :as web.core]
   [integrant.core]))

(def get-platform-by-eid
  (web.core/standard-fetch handlers.social-media/get-platform-by-eid :social-media/platform))

(defmethod integrant.core/init-key ::get-platform
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       router                :reitit.core/router
       :as                   _request}]
    (web.core/to-fetch-response
     schema/social-media-platform-resource
     {:router router :hostname (:hostname dependencies)}
     (cats/extract
      (cats/>>=
       (either/right eid)
       (partial get-platform-by-eid dependencies))))))
