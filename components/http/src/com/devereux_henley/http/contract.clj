(ns com.devereux-henley.http.contract
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core]))

(defn encode-value
  [route-data resource-schema value]
  (malli.core/encode resource-schema value (schema.contract/model-transformer route-data)))

(defn handle-fetch-response
  [resource-schema route-data thunk]
  (let [result (thunk)]
    (if (= :missing/resource (:type result))
      {:status 404 :body result}
      {:status 200 :body (encode-value route-data resource-schema result)})))

(defn handle-create-response
  [resource-schema route-data thunk]
  {:status 201 :body (encode-value route-data resource-schema (thunk))})

(defn embed-query-parameter
  "Returns a Malli query parameter schema for the embed param whose valid values
   are derived from the keys of the given registry map. Swagger will enumerate
   the valid embed keys automatically."
  [registry]
  (schema.contract/to-schema
   [:map
    [:embed {:optional true} [:set (into [:enum] (keys registry))]]]))

(defn apply-embeds
  "Threads each requested embed fn from registry through the model.
   All keys are guaranteed valid by Malli before the handler runs.
   Returns model unchanged when embed-keys is nil or empty.
   Short-circuits on :missing/resource."
  [registry dependencies embed-keys model]
  (if (= :missing/resource (:type model))
    model
    (reduce
     (fn [acc embed-key]
       ((get registry embed-key) dependencies acc))
     model
     (or embed-keys #{}))))

(defn query-param->vec
  "Normalises a reitit query parameter that may arrive as nil, a single
   string, or a collection of strings into a vector of strings. Returns
   nil when the input is nil so callers can distinguish an absent param
   from one explicitly set to an empty list."
  [v]
  (cond
    (nil? v)        nil
    (string? v)     [v]
    (sequential? v) (vec v)
    :else           nil))
