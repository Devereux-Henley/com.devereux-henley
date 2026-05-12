(ns com.devereux-henley.rts-api.produces-enforcement
  "Reitit middleware that enforces the matched route's `:produces` against
  the request's Accept header. Reitit's built-in `:produces` declaration
  is informational only; muuntaja negotiates against globally-registered
  formats, so without this middleware a route saying `:produces
  [\"application/json\"]` will still happily serve any other registered
  format if asked. This middleware closes the gap: a request whose Accept
  header overlaps none of the produced types is rejected with 406."
  (:require
   [clojure.string :as string]
   [reitit.ring :as ring]))

(defn- accept-wildcard?
  "True when the Accept header is missing, blank, or contains `*/*`."
  [accept-header]
  (or (string/blank? accept-header)
      (string/includes? accept-header "*/*")))

(defn- accept-allows?
  "Does `accept-header` permit at least one of the `produces` media
   types? Match is substring-based so `application/json;q=0.9` counts as
   acceptable for `application/json`. Matches are case-insensitive."
  [accept-header produces]
  (or (accept-wildcard? accept-header)
      (let [lc (string/lower-case accept-header)]
        (some #(string/includes? lc (string/lower-case %)) produces))))

(defn- route-produces
  "Reads the matched route's `:produces` for the request method. Reitit
   keeps method-specific data under method keys in `:data`, e.g.
   `{:data {:get {:produces [...]}}}` — so we look it up by the request
   method. Also accepts a top-level `:produces` for routes that declare
   one verb only."
  [request]
  (when-let [match (ring/get-match request)]
    (let [data (:data match)]
      (or (get-in data [(:request-method request) :produces])
          (:produces data)))))

(defn wrap-enforce-produces
  "Returns 406 Not Acceptable when the matched route declares `:produces`
   and the request's Accept header does not include at least one of those
   media types. No-op when the matched route has no `:produces` (route
   accepts whatever muuntaja can negotiate globally)."
  [handler]
  (fn [request]
    (let [produces (route-produces request)
          accept   (get-in request [:headers "accept"])]
      (if (or (nil? produces)
              (empty? produces)
              (accept-allows? accept produces))
        (handler request)
        {:status  406
         :headers {"Content-Type" "text/plain; charset=utf-8"}
         :body    "Not Acceptable"}))))
