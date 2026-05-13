(ns com.devereux-henley.rts-api.produces-enforcement-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-api.produces-enforcement :as produces-enforcement]))

(defn- ok-handler
  [_request]
  {:status 200 :body :handler-ran})

(def ^:private wrapped
  (produces-enforcement/wrap-enforce-produces ok-handler))

(defn- request-with
  "Builds a fake reitit-style request whose matched route declares the
   given :produces (under the request's verb, mirroring reitit's method-
   keyed shape) and whose Accept header is what the test asserts on."
  [produces accept]
  (cond-> {:request-method    :get
           :reitit.core/match {:data {:get {:produces produces}}}}
    accept (assoc :headers {"accept" accept})))

(defn- accept-after
  "Returns the Accept header the handler observed — used to confirm the
   middleware rewrote Accept to the route's first produced type."
  [request]
  (get-in request [:headers "accept"]))

(def ^:private capture-handler
  "Echoes the Accept header back so wildcard-rewrite tests can assert on it."
  (produces-enforcement/wrap-enforce-produces
   (fn [request]
     {:status 200 :body (accept-after request)})))

(deftest accept-includes-a-produced-type
  (testing "Accept header listing exactly the produced type → handler runs"
    (is (= :handler-ran (:body (wrapped (request-with ["application/json"] "application/json"))))))
  (testing "Accept header listing one of several produced types → handler runs"
    (is (= :handler-ran (:body (wrapped (request-with ["application/json" "application/hal+json"]
                                                      "application/hal+json"))))))
  (testing "Accept header carrying q-values for a produced type → handler runs"
    (is (= :handler-ran (:body (wrapped (request-with ["application/json"] "application/json;q=0.9")))))))

(deftest accept-mismatches-produced-types
  (testing "Accept asks for htmx+html but route only produces JSON → 406"
    (is (= 406 (:status (wrapped (request-with ["application/json"] "application/htmx+html"))))))
  (testing "Accept asks for text/html but route produces htmx+html only → 406"
    (is (= 406 (:status (wrapped (request-with ["application/htmx+html"] "text/html")))))))

(deftest accept-wildcards-rewritten-to-first-produced
  (testing "Accept: */* → handler runs; Accept rewritten to route's first produced type"
    (is (= "application/htmx+html"
           (:body (capture-handler (request-with ["application/htmx+html"] "*/*"))))))
  (testing "missing Accept header → handler runs; Accept rewritten"
    (is (= "application/json"
           (:body (capture-handler (request-with ["application/json"] nil))))))
  (testing "blank Accept header → handler runs; Accept rewritten"
    (is (= "application/json"
           (:body (capture-handler (request-with ["application/json"] ""))))))
  (testing "browser Accept (text/html, */* fallback, no produced type listed) → rewritten to produces[0]"
    (is (= "application/htmx+html"
           (:body (capture-handler
                   (request-with ["application/htmx+html"]
                                 "text/html,application/xhtml+xml,*/*;q=0.8")))))))

(deftest no-produces-on-route-passes
  (testing "matched route without :produces → handler runs regardless of Accept"
    (is (= :handler-ran (:body (wrapped (request-with nil "application/htmx+html"))))))
  (testing "matched route with empty :produces → handler runs"
    (is (= :handler-ran (:body (wrapped (request-with [] "application/htmx+html")))))))

(deftest case-insensitive-matching
  (testing "Accept header uppercase, produced lowercase → handler runs"
    (is (= :handler-ran (:body (wrapped (request-with ["application/json"] "APPLICATION/JSON"))))))
  (testing "Accept header lowercase, produced mixed-case → handler runs"
    (is (= :handler-ran (:body (wrapped (request-with ["Application/JSON"] "application/json")))))))
