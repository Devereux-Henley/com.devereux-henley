(ns com.devereux-henley.rose-api.web.flower
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.rose-api.schema :as schema]
   [com.devereux-henley.rose-api.handlers.flower :as handlers.flower]
   [integrant.core]
   [malli.generator]))

(defn to-fetch-response
  [resource-schema value]
  (condp instance? value
    clojure.lang.ExceptionInfo
    (case (:error/kind (ex-data value))
      :error/missing {:status 404
                      :body   nil}
      :error/unknown {:status 500
                      :body   (select-keys [:error/kind :error/message] (ex-data value))}
      {:status 500
       :body   (select-keys [:error/kind :error/message] (ex-data value))})
    Throwable
    {:status 500
     :body   nil}
    {:status 200
     :body   (malli.core/decode resource-schema value schema/model-transformer)}))

;; TODO :reitit.core/router from request
(defn get-flower-by-id
  [dependencies id]
  (try
    (if-let [flower (handlers.flower/get-flower-by-id dependencies id)]
      (either/right flower)
      (either/left (ex-info
                    "No flower with given id."
                    {:error/kind :error/missing
                     :model/id   id
                     :model/type :flower/flower})))
    (catch Exception exc
      (println exc)
      (either/left (ex-info
                    "Failed to fetch flower."
                    {:error/kind :error/unknown
                     :model/id   id
                     :model/type :flower/flower}
                    exc)))))

(defmethod integrant.core/init-key ::get-flower
  [_init-key dependencies]
  (fn [{{{:keys [id]} :path} :parameters :as request}]
    (to-fetch-response
     schema/flower-resource
     (cats/extract
      (cats/>>=
       (either/right id)
       (partial get-flower-by-id dependencies))))))

(defmethod integrant.core/init-key ::create-flower
  [_init-key _dependencies]
  (fn [_request-map]
    {:status 201
     :body   nil}))

(defmethod integrant.core/init-key ::get-my-flower-collection
  [_init-key _dependencies]
  (fn [{{{:keys [_id]} :path} :parameters}]
    {:status 200
     :body   (malli.generator/generate
              schema/flower-collection-resource)}))

(defmethod integrant.core/init-key ::get-recent-flower-collection
  [_init-key _dependencies]
  (fn [{{{:keys [_id]} :path} :parameters}]
    {:status 200
     :body   (malli.generator/generate
              schema/flower-collection-resource)}))
