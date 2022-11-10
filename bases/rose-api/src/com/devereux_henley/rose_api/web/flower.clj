(ns com.devereux-henley.rose-api.web.flower
  (:require
   [cats.core :as cats]
   [cats.monad.either :as either]
   [com.devereux-henley.rose-api.schema :as schema]
   [com.devereux-henley.rose-api.handlers.flower :as handlers.flower]
   [integrant.core]
   [malli.generator])
  (:import
   [java.time LocalDate]))

(defn decode-flower
  [flower]
  (malli.core/decode schema/flower-resource flower schema/model-transformer))

(defn to-fetch-response
  [resource-schema value]
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
     :body   (decode-flower value)}))

(defn to-collection-resource
  [resource-schema since user_id value]
  (condp instance? value
    clojure.lang.ExceptionInfo
    (case (:error/kind (ex-data value))
      :error/unknown {:status 500
                      :body (select-keys [:error/kind :model/id :error/message])}
      {:status 500
       :body (select-keys [:error/kind :model/id :error/message] (ex-data value))})
    Throwable
    {:status 500
     :body {}}
    {:status 200
     :body {:type :collection/flower
            :_links {:self (str "/api/flower?since=" since "&for=" user_id)}
            :_embedded {:results (mapv decode-flower value)}}}))

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

(defn get-flowers-by-user-id
  [dependencies user_id]
  (try
    (either/right (handlers.flower/get-flowers-by-user-id dependencies user_id))
    (catch Exception exc
      (either/left (ex-info
                    "Failed to fetch flowers for user."
                    {:error/kind :error/unknown
                     :model/id   user_id
                     :model/type :identity/user}
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
  [_init-key dependencies]
  (fn [{{{:keys [_id]} :path} :parameters}]
    (to-collection-resource
     schema/flower-resource
     (java.time.LocalDate/now)
     #uuid "acee55c5-a491-470e-a558-7bc4c79e7c21"
     (cats/extract
      (cats/>>=
       (either/right #uuid "acee55c5-a491-470e-a558-7bc4c79e7c21")
       (partial get-flowers-by-user-id dependencies))))))

(defmethod integrant.core/init-key ::get-recent-flower-collection
  [_init-key _dependencies]
  (fn [{{{:keys [_id]} :path} :parameters}]
    {:status 200
     :body   (malli.generator/generate
              schema/flower-collection-resource)}))
