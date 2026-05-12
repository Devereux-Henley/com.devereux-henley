(ns com.devereux-henley.rts-api.method-override
  "Outer ring middleware that unwraps `?_method=PUT|PATCH|DELETE` on POST
  requests so plain HTML <form> elements can drive non-POST mutations
  without JavaScript. HTMX-driven forms use real HTTP verbs and bypass
  this entirely."
  (:require
   [clojure.string :as string]
   [ring.util.codec :as codec]))

(def ^:private overridable-verbs
  #{"put" "patch" "delete"})

(defn- read-method-override
  "Returns the lower-cased `_method` query-parameter value, or nil when
   the query string is absent or does not contain `_method`."
  [query-string]
  (when-not (string/blank? query-string)
    (some (fn [pair]
            (let [[k v] (string/split pair #"=" 2)]
              (when (and v (= (codec/url-decode k) "_method"))
                (string/lower-case (codec/url-decode v)))))
          (string/split query-string #"&"))))

(defn wrap-method-override
  "Ring middleware. On POST requests with `?_method=PUT|PATCH|DELETE`,
   rewrites `:request-method` to the overridden verb before delegating
   to `handler`. Other requests pass through unchanged."
  [handler]
  (fn [{:keys [request-method query-string] :as request}]
    (let [override (when (= :post request-method)
                     (read-method-override query-string))]
      (if (contains? overridable-verbs override)
        (handler (assoc request :request-method (keyword override)))
        (handler request)))))
