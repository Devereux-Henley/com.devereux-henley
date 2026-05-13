(ns com.devereux-henley.rts-api.produces-enforcement
  "Reitit middleware that reconciles the matched route's `:produces`
  with the request's Accept header. Reitit's built-in `:produces` is
  documentation-only — muuntaja negotiates against globally-registered
  formats and can pick a format the route never declared. This middleware
  closes the gap two ways: it 406s requests whose Accept overlaps nothing
  the route produces, and it rewrites wildcard-only Accept headers to the
  route's first produced type so muuntaja picks that type rather than
  whichever globally-registered format happens to share the wildcard."
  (:require
   [clojure.string :as string]
   [reitit.ring :as ring]))

(defn- accept-wildcard?
  "True when the Accept header is missing, blank, or contains `*/*`."
  [accept-header]
  (or (string/blank? accept-header)
      (string/includes? accept-header "*/*")))

(defn- accept-includes-produced?
  "True when the Accept header literally names one of the produced
   media types. Match is substring-based so `application/json;q=0.9`
   counts as `application/json`. Case-insensitive."
  [accept-header produces]
  (when accept-header
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
  "Three cases when the matched route declares `:produces`:

   1. Accept literally names one of the produced types → pass through.
      Muuntaja will negotiate to that type.
   2. Accept is wildcard (missing, blank, or contains `*/*`) but does
      not literally name a produced type → rewrite `:headers \"accept\"`
      to the first produced type so muuntaja picks the route's own
      type rather than another globally-registered type that happens
      to be listed in the Accept alongside the wildcard.
   3. Neither — 406 Not Acceptable.

   No-op when the route has no `:produces` declaration."
  [handler]
  (fn [request]
    (let [produces (route-produces request)
          accept   (get-in request [:headers "accept"])]
      (cond
        (or (nil? produces) (empty? produces))
        (handler request)

        (accept-includes-produced? accept produces)
        (handler request)

        (accept-wildcard? accept)
        (handler (assoc-in request [:headers "accept"] (first produces)))

        :else
        {:status  406
         :headers {"Content-Type" "text/plain; charset=utf-8"}
         :body    "Not Acceptable"}))))
