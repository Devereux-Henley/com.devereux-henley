(ns com.devereux-henley.rts-api.web.core
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core]))

(defn encode-value
  [route-data resource-schema value]
  (malli.core/encode resource-schema value (schema.contract/model-transformer route-data)))

(defn to-fetch-response
  [resource-schema route-data value]
  (println value)
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
