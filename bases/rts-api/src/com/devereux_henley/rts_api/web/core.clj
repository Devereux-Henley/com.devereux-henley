(ns com.devereux-henley.rts-api.web.core
  (:require
   [cats.monad.either :as either]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core]
   [taoensso.timbre :as log]))

(defn encode-value
  [route-data resource-schema value]
  (malli.core/encode resource-schema value (schema.contract/model-transformer route-data)))

(defn to-fetch-response
  [resource-schema route-data value]
  (log/debug value)
  (condp instance? value
    clojure.lang.ExceptionInfo
    (case (:error/kind (ex-data value))
      :error/missing {:status 404
                      :body   (select-keys [:error/kind :model/id :error/message] (ex-data value))}
      :error/unknown {:status 500
                      :body   (select-keys [:error/kind :model/id :error/message] (ex-data value))}
      {:status 500
       :body   (select-keys [:error/kind :model/id :error/message] (ex-data value))})
    Throwable
    {:status 500
     :body   {}}
    {:status 200
     :body   (encode-value route-data resource-schema value)}))

(defn standard-fetch
  [fetch-fn resource-type]
  (fn [dependencies eid]
    (try
      (if-let [resource (fetch-fn dependencies eid)]
        (either/right resource)
        (either/left (ex-info
                      (str
                       "No "
                       (name resource-type)
                       " with given eid.")
                      {:error/kind :error/missing
                       :model/id   eid
                       :model/type resource-type})))
      (catch Exception exc
        (log/error exc)
        (either/left (ex-info
                      (str "Failed to fetch " (name resource-type))
                      {:error/kind :error/unknown
                       :model/id   eid
                       :model/type resource-type}
                      exc))))))
