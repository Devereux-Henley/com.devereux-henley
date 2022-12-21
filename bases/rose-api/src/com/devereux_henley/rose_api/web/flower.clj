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
                      :body   (select-keys [:error/kind :model/eid :error/message] (ex-data value))}
      :error/unknown {:status 500
                      :body   (select-keys [:error/kind :model/eid :error/message] (ex-data value))}
      {:status 500
       :body   (select-keys [:error/kind :model/eid :error/message] (ex-data value))})
    Throwable
    {:status 500
     :body   {}}
    {:status 200
     :body   (decode-flower value)}))

(defn to-create-response
  [resource-schema value]
  (condp instance? value
    clojure.lang.ExceptionInfo
    (case (:error/kind (ex-data value))
      :error/conflict {:status 409
                       :body   (select-keys [:error/kind :model/eid :error/message] (ex-data value))}
      :error/unknown  {:status 500
                       :body   (select-keys [:error/kind :model/eid :error/message] (ex-data value))}
      {:status 500
       :body   (select-keys [:error/kind :model/eid :error/message] (ex-data value))})
    Throwable
    {:status 500
     :body   {}}
    {:status 201
     :body   (decode-flower value)}))

(defn to-update-response
  [resource-schema value]
  (condp instance? value
    clojure.lang.ExceptionInfo
    (case (:error/kind (ex-data value))
      :error/conflict {:status 409
                       :body   (select-keys [:error/kind :model/eid :error/message] (ex-data value))}
      :error/unknown  {:status 500
                       :body   (select-keys [:error/kind :model/eid :error/message] (ex-data value))}
      {:status 500
       :body   (select-keys [:error/kind :model/eid :error/message] (ex-data value))})
    Throwable
    {:status 500
     :body   {}}
    {:status 200
     :body   (decode-flower value)}))

(defn to-collection-resource
  [resource-schema since user_eid value]
  (condp instance? value
    clojure.lang.ExceptionInfo
    (case (:error/kind (ex-data value))
      :error/unknown {:status 500
                      :body (select-keys [:error/kind :model/eid :error/message])}
      {:status 500
       :body (select-keys [:error/kind :model/eid :error/message] (ex-data value))})
    Throwable
    {:status 500
     :body {}}
    {:status 200
     :body {:type :collection/flower
            :_links {:self (str "/api/flower?since=" since "&for=" user_eid)}
            :_embedded {:results (mapv decode-flower value)}}}))

;; TODO :reitit.core/router from request
(defn get-flower-by-eid
  [dependencies eid]
  (try
    (if-let [flower (handlers.flower/get-flower-by-eid dependencies eid)]
      (either/right flower)
      (either/left (ex-info
                    "No flower with given id."
                    {:error/kind :error/missing
                     :model/eid   eid
                     :model/type :flower/flower})))
    (catch Exception exc
      (println exc)
      (either/left (ex-info
                    "Failed to fetch flower."
                    {:error/kind :error/unknown
                     :model/eid   eid
                     :model/type :flower/flower}
                    exc)))))

(defn create-flower
  [dependencies create-specification]
  (try
    (either/right (handler.flower/create-flower dependencies create-specification))
    (catch Exception exc
      (either/left (ex-info
                    "Failed to create flower with input specification."
                    {:error/kind :error/unknown
                     :model/eid (:eid create-specification)
                     :model/version (:version create-specification)
                     :model/type :flower/flower}
                    exc)))))

(defn get-flowers-by-user-eid
  [dependencies user_eid]
  (try
    (either/right (handlers.flower/get-flowers-by-user-eid dependencies user_eid))
    (catch Exception exc
      (either/left (ex-info
                    "Failed to fetch flowers for user."
                    {:error/kind :error/unknown
                     :model/eid   user_eid
                     :model/type :identity/user}
                    exc)))))

(defmethod integrant.core/init-key ::get-flower
  [_init-key dependencies]
  (fn [{{{:keys [eid]} :path} :parameters :as request}]
    (to-fetch-response
     schema/flower-resource
     (cats/extract
      (cats/>>=
       (either/right eid)
       (partial get-flower-by-eid dependencies))))))

(defmethod integrant.core/init-key ::create-flower
  [_init-key dependencies]
  (fn [{{:keys [path body query]} :parameters}]
    (to-create-response
     schema/flower-resource
     (cats/extract
      (cats/>>=
       (either/right (merge {:version 1} path body query))
       (partial create-flower dependencies))))))

(defmethod integrant.core/init-key ::get-my-flower-collection
  [_init-key dependencies]
  (fn [_request-map]
    (to-collection-resource
     schema/flower-resource
     (java.time.LocalDate/now)
     #uuid "acee55c5-a491-470e-a558-7bc4c79e7c21"
     (cats/extract
      (cats/>>=
       (either/right #uuid "acee55c5-a491-470e-a558-7bc4c79e7c21")
       (partial get-flowers-by-user-eid dependencies))))))

(defmethod integrant.core/init-key ::get-recent-flower-collection
  [_init-key _dependencies]
  (fn [{{{:keys [_eid]} :path} :parameters}]
    {:status 200
     :body   (malli.generator/generate
              schema/flower-collection-resource)}))
