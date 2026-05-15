(ns com.devereux-henley.rts-web.web.actions.common
  "Shared response builders for /actions handlers.

  Every /actions endpoint follows one of three shapes:
    1. Mutation success → status + body (htmx+html via the component
       view registry; templates may include OOB swaps that update
       sidebar/budget in-place) plus an HX-Trigger header naming the
       kebab-case event.
       The Trigger header is the channel future listener-bound
       /components fragments will react to.
    2. Mutation creates a resource users navigate to → 200 + HX-Redirect.
       HTMX issues a client-side navigation. A 3xx response would make
       the fetch auto-follow before HTMX could see the header.
    3. Mutation failed → HTML error fragment (role=alert) with the
       relevant 4xx status."
  (:require
   [clojure.string :as string]))

(defn trigger-response
  "Mutation success with a 200 status. `body` will be encoded as
   htmx+html by muuntaja via the component view registry."
  [event-name body]
  {:status  200
   :headers {"HX-Trigger" event-name}
   :body    body})

(defn trigger-status-response
  "Same as `trigger-response` but with an explicit status code, for
   creation flows that return 201."
  [status event-name body]
  {:status  status
   :headers {"HX-Trigger" event-name}
   :body    body})

(defn redirect-response
  "Created-and-go-somewhere. Returns 200 with HX-Redirect so HTMX
   navigates client-side. A 3xx status would make the browser auto-
   follow the redirect inside the fetch — HTMX would never see the
   header, and `window.location` would not change."
  [location]
  {:status  200
   :headers {"HX-Redirect" location}})

(defn error-fragment
  "Failure: 4xx with an HTML alert fragment. Templates' hx-target swaps
   this where appropriate; without an explicit hx-target HTMX puts it
   in the form itself, which is acceptable for inline form errors."
  [status message]
  {:status  status
   :headers {"Content-Type" "application/htmx+html; charset=utf-8"}
   :body    (str "<section class=\"resource\" role=\"alert\">"
                 "<p>" (some-> message
                               (string/replace "&" "&amp;")
                               (string/replace "<" "&lt;")
                               (string/replace ">" "&gt;")) "</p>"
                 "</section>")})
