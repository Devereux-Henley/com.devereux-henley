(ns com.devereux-henley.rts-api.web.social-media
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.rts-api.schema :as schema]
   [com.devereux-henley.rts-api.handlers.social-media :as handlers.social-media]
   [com.devereux-henley.rts-api.web.core :as web.core]
   [integrant.core])
  (:import
   [java.time LocalDate]))

;; TODO :reitit.core/router from request
(defn get-platform-by-eid
  [dependencies eid]
  (try
    (if-let [platform (handlers.social-media/get-platform-by-eid dependencies eid)]
      (either/right platform)
      (either/left (ex-info
                    "No social media platform with given eid."
                    {:error/kind :error/missing
                     :model/id   eid
                     :model/type :platform/platform})))
    (catch Exception exc
      (println exc)
      (either/left (ex-info
                    "Failed to fetch social media platform."
                    {:error/kind :error/unknown
                     :model/id   eid
                     :model/type :platform/platform}
                    exc)))))

(defmethod integrant.core/init-key ::get-platform
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters
       router                :reitit.core/router
       :as                   _request}]
    (web.core/to-fetch-response
     schema/platform-resource
     router
     (cats/extract
      (cats/>>=
       (either/right eid)
       (partial get-platform-by-eid dependencies))))))
