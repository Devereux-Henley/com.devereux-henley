(ns com.devereux-henley.rts-api.web.core
  (:require
   [cats.monad.either :as either]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core]
   [taoensso.timbre :as log]
   [com.devereux-henley.rts-api.schema :as schema]))

(defn encode-value
  [route-data resource-schema value]
  (malli.core/encode resource-schema value (schema.contract/model-transformer route-data)))

(def error-keys [:error/kind
                 :model/eid
                 :collection/since
                 :collection/size
                 :collection/offset
                 :error/message])

(defn to-fetch-response
  [resource-schema route-data value]
  (log/debug value)
  (condp instance? value
    clojure.lang.ExceptionInfo
    (case (:error/kind (ex-data value))
      :error/missing {:status 404
                      :body   (select-keys error-keys (ex-data value))}
      :error/unknown {:status 500
                      :body   (select-keys error-keys (ex-data value))}
      {:status 500
       :body   (select-keys error-keys (ex-data value))})
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
                       :model/eid  eid
                       :model/type resource-type})))
      (catch Exception exc
        (log/error exc)
        (either/left (ex-info
                      (str "Failed to fetch " (name resource-type))
                      {:error/kind :error/unknown
                       :model/eid  eid
                       :model/type resource-type}
                      exc))))))

(defn standard-fetch-collection
  [fetch-fn resource-type]
  (fn [dependencies {:keys [since size offset] :or {size 10 offset 0} :as specification}]
    (try
      (either/right {:type      resource-type
                     :specification (-> specification (assoc :size size) (assoc :offset offset))
                     :_embedded {:results (fetch-fn dependencies since size offset)}})
      (catch Exception exc
        (log/error exc)
        (either/left (ex-info
                      (str "Failed to fetch " (name resource-type))
                      {:error/kind        :error/unknown
                       :collection/since  since
                       :collection/size   size
                       :collection/offset offset
                       :model/type        resource-type}
                      exc))))))

(defn standard-load-embedded
  [fetch-fn embedded-resource-key model-resource-type]
  (fn [dependencies model]
    (try
      (let [embedded-resources (fetch-fn dependencies (:eid model))]
        (either/right (assoc-in model [:_embedded embedded-resource-key] embedded-resources)))
      (catch Exception exc
        (log/error exc)
        (either/left (ex-info
                      (format "Failed to fetch %s for specified %s."
                              embedded-resource-key
                              (name model-resource-type))
                      {:error/kind :error/unknown
                       :model/eid  (:eid model)
                       :model/type model-resource-type}
                      exc))))))
