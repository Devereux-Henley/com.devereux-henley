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

  Collections (`:model/type :model/collection`) are passed through
  unchanged here — their `_links.self` requires a
  `:collection/link`-annotated `:specification` that none of our list
  endpoints currently use, so collection handlers continue to inject
  `_links` themselves."
  (:require
   [com.devereux-henley.schema.contract :as schema]
   [integrant.core]
   [malli.core]
   [malli.transform]
   [reitit.core]))

(defn- response-schema
  [request status]
  (let [match  (reitit.core/match-by-path
                (:reitit.core/router request)
                (:uri request))
        method (:request-method request)]
    (get-in match [:result method :data :responses status :body])))

(defn- model-only-transformer
  "Like `schema.contract/model-transformer` but skips collections.
   Collections (`:model/type :model/collection`) would otherwise
   crash because our list responses don't carry `:specification`."
  [route-data]
  (malli.transform/transformer
   {:name     :model
    :encoders {:map {:compile
                     (fn [schema _]
                       (if (= :model/model (:model/type (malli.core/properties schema)))
                         (schema/handle-model-transform route-data schema)
                         identity))}}}))

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
                                    (model-only-transformer
                                     {:hostname hostname :router router})))
          response)))))

(defmethod integrant.core/init-key ::middleware
  [_init-key {:keys [hostname]}]
  (wrap-model-transform hostname))
