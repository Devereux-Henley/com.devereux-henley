(ns com.devereux-henley.rts-api.web.core
  (:require
   [cats.monad.either :as either]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core]
   [taoensso.timbre :as log]))

(defn encode-value
  [route-data resource-schema value]
  (malli.core/encode resource-schema value (schema.contract/model-transformer route-data)))

(def error-keys [:error/kind
                 :model/eid
                 :collection/since
                 :collection/size
                 :collection/offset
                 :error/message])

(defn to-error-response
  [value]
  (log/debug value)
  (case (:error/kind (ex-data value))
    :error/invalid {:status 400
                    :body   (select-keys error-keys (ex-data value))}
    :error/missing {:status 404
                    :body   (select-keys error-keys (ex-data value))}
    :error/conflict {:status 409
                     :body   (select-keys error-keys (ex-data value))}
    :error/unknown {:status 500
                    :body   (select-keys error-keys (ex-data value))}
    {:status 500
     :body   (select-keys error-keys (ex-data value))}))

(defn to-success-fetch-response
  [resource-schema route-data value]
  {:status 200
   :body   (encode-value route-data resource-schema value)})

(defn to-success-create-response
  [resource-schema route-data value]
  {:status 201
   :body   (encode-value route-data resource-schema value)})

(defn handle-fetch-response
  [resource-schema route-data either-value]
  (either/branch
   either-value
   to-error-response
   (partial to-success-fetch-response resource-schema route-data)))

(defn handle-create-response
  [resource-schema route-data either-value]
  (either/branch
   either-value
   to-error-response
   (partial to-success-create-response resource-schema route-data)))

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
  (fn [dependencies {:keys [since size offset] :as specification}]
    (let [size (or size 10)
          offset (or offset 0)]
      (try
        (either/right {:type          resource-type
                       :specification (-> specification (assoc :size size) (assoc :offset offset))
                       :_embedded     {:results (fetch-fn dependencies since size offset)}})
        (catch Exception exc
          (log/error exc)
          (either/left (ex-info
                        (str "Failed to fetch " (name resource-type))
                        {:error/kind        :error/unknown
                         :collection/since  since
                         :collection/size   size
                         :collection/offset offset
                         :model/type        resource-type}
                        exc)))))))

(defn standard-create
  [create-fn resource-type]
  (fn [dependencies create-specification]
    (try
      (either/right (create-fn dependencies create-specification))
      (catch Exception exc
        (log/error exc)
        (either/left (ex-info
                      (str "Failed to create " (name resource-type))
                      {:error/kind :error/unknown
                       :model/eid  (:eid create-specification)
                       :model/type resource-type}
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
