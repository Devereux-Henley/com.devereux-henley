(ns com.devereux-henley.rts-api.model-transform
  "Ring middleware that runs malli's model-transformer on every 2xx
  response whose matched route declares a `:responses {<status> {:body
  <schema>}}`. The transformer reads `:model/link` annotations on
  schema fields (recursively, via malli's schema walk) and resolves
  them into `_links` entries on the response body using the request's
  reitit router to build hostname-qualified URLs.

  Handlers therefore just return `{:status 200 :body raw-map}` —
  `_links` shows up automatically, including on any nested
  `:model/model` resources reachable through `:_embedded`.

  Maps without `:model/type :model/model` pass through unchanged."
  (:require
   [com.devereux-henley.schema.contract :as schema]
   [integrant.core]
   [malli.core]
   [reitit.core]))

(defn- response-schema
  [request status]
  (let [match  (reitit.core/match-by-path
                (:reitit.core/router request)
                (:uri request))
        method (:request-method request)]
    (get-in match [:result method :data :responses status :body])))

(defn wrap-model-transform
  [hostname]
  (fn [handler]
    (fn [request]
      (let [response (handler request)
            status   (:status response)
            body     (:body response)
            router   (:reitit.core/router request)
            schema   (when (and (integer? status) (<= 200 status 299) router)
                       (response-schema request status))]
        (if (and schema (map? body))
          (assoc response :body
                 (malli.core/encode schema body
                                    (schema/model-transformer
                                     {:hostname hostname :router router})))
          response)))))

(defmethod integrant.core/init-key ::middleware
  [_init-key {:keys [hostname]}]
  (wrap-model-transform hostname))
