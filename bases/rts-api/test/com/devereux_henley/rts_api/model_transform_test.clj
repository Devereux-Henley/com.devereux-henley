(ns com.devereux-henley.rts-api.model-transform-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-api.model-transform :as model-transform]
   [com.devereux-henley.schema.contract :as schema]
   [malli.util]
   [reitit.coercion.malli]
   [reitit.ring]))

(def ^:private tournament-resource
  (schema/to-schema
   [:map {:model/type         :model/model
          :model/sub-resources {:matches :tournament/matches}}
    [:eid {:model/link :tournament/by-eid} :uuid]
    [:type [:= :tournament/tournament]]
    [:name :string]
    [:game-eid {:model/link :game/by-eid} :uuid]
    [:_links [:map
              [:self :url]
              [:game :url]
              [:matches :url]]]]))

(def ^:private match-resource
  (schema/to-schema
   [:map {:model/type :model/model}
    [:eid {:model/link :match/by-eid} :uuid]
    [:type [:= :tournament/match]]
    [:tournament-eid {:model/link :tournament/by-eid} :uuid]
    [:_links [:map [:self :url] [:tournament :url]]]]))

(def ^:private tournament-with-embedded-match
  (schema/to-schema
   [:map {:model/type         :model/model
          :model/sub-resources {:matches :tournament/matches}}
    [:eid {:model/link :tournament/by-eid} :uuid]
    [:type [:= :tournament/tournament]]
    [:name :string]
    [:game-eid {:model/link :game/by-eid} :uuid]
    [:_links [:map
              [:self :url]
              [:game :url]
              [:matches :url]]]
    [:_embedded [:map [:current-match match-resource]]]]))

(def ^:private noop (constantly nil))

(def ^:private router
  (reitit.ring/router
   [["/api/game/:eid"                             {:name :game/by-eid :get {:handler noop}}]
    ["/api/tournament/:eid"                       {:name :tournament/by-eid :get {:handler noop}}]
    ["/api/tournament/:tournament-eid/match"      {:name :tournament/matches
                                                   :get  {:handler   noop
                                                          :responses {200 {:body tournament-resource}}}}]
    ["/api/tournament/:tournament-eid/match/:eid" {:name :match/by-eid
                                                   :get  {:handler   noop
                                                          :responses {200 {:body match-resource}}}}]
    ["/api/tournament/:eid/embedded"              {:get {:handler   noop
                                                         :responses {200 {:body tournament-with-embedded-match}}}}]
    ["/api/tournament/:eid/no-schema"             {:get {:handler noop}}]
    ["/api/tournament/:eid/resource"              {:get {:handler   noop
                                                         :responses {200 {:body tournament-resource}}}}]]
   {:data {:coercion (reitit.coercion.malli/create
                      {:compile malli.util/closed-schema})}}))

(def ^:private hostname "http://test.local")

(defn- run-middleware
  "Wraps `handler` with the model-transform middleware (configured with
   the test hostname) and invokes it on a fake reitit request for
   `path`. Returns the resulting response map."
  [path handler]
  (let [middleware (model-transform/wrap-model-transform hostname)
        wrapped    (middleware handler)
        request    {:request-method        :get
                    :uri                   path
                    :reitit.core/router    router}]
    (wrapped request)))

;; ─── Happy path ────────────────────────────────────────────────────────────

(def ^:private tournament-eid #uuid "11111111-1111-1111-1111-111111111111")
(def ^:private game-eid       #uuid "22222222-2222-2222-2222-222222222222")
(def ^:private match-eid      #uuid "33333333-3333-3333-3333-333333333333")

(defn- handler-returning
  [body]
  (constantly {:status 200 :body body}))

(deftest fk-links-are-resolved-on-2xx-response
  (testing "FK annotations populate _links.self and _links.<fk> for a model schema"
    (let [response (run-middleware (str "/api/tournament/" tournament-eid "/resource")
                                   (handler-returning
                                    {:type     :tournament/tournament
                                     :eid      tournament-eid
                                     :name     "T"
                                     :game-eid game-eid}))
          links    (get-in response [:body :_links])]
      (is (= (str hostname "/api/tournament/" tournament-eid) (:self links)))
      (is (= (str hostname "/api/game/" game-eid) (:game links))))))

(deftest sub-resource-links-resolve-via-model-sub-resources
  (testing ":model/sub-resources fills _links for routes that hang off the resource"
    (let [response (run-middleware (str "/api/tournament/" tournament-eid "/resource")
                                   (handler-returning
                                    {:type     :tournament/tournament
                                     :eid      tournament-eid
                                     :name     "T"
                                     :game-eid game-eid}))]
      (is (= (str hostname "/api/tournament/" tournament-eid "/match")
             (get-in response [:body :_links :matches]))
          "matches route uses :tournament-eid slot, filled from resource's own :eid"))))

(deftest nested-embedded-resource-also-gets-links
  (testing "nested :model/model maps reachable through :_embedded are transformed too"
    (let [response (run-middleware (str "/api/tournament/" tournament-eid "/embedded")
                                   (handler-returning
                                    {:type           :tournament/tournament
                                     :eid            tournament-eid
                                     :name           "T"
                                     :game-eid       game-eid
                                     :_embedded      {:current-match
                                                      {:type           :tournament/match
                                                       :eid            match-eid
                                                       :tournament-eid tournament-eid}}}))
          embedded (get-in response [:body :_embedded :current-match :_links])]
      (is (= (str hostname "/api/tournament/" tournament-eid "/match/" match-eid)
             (:self embedded)))
      (is (= (str hostname "/api/tournament/" tournament-eid) (:tournament embedded))))))

;; ─── Pass-through cases ────────────────────────────────────────────────────

(deftest non-2xx-status-bypasses-encoder
  (testing "Status 404 → response untouched even when the route declares a schema"
    (let [body     {:type :missing/resource :name "tournament"}
          response (run-middleware (str "/api/tournament/" tournament-eid "/resource")
                                   (constantly {:status 404 :body body}))]
      (is (= body (:body response)) "encoder did not run; raw body returned"))))

(deftest body-without-router-is-passed-through
  (testing "When the request carries no reitit router, the middleware is a no-op"
    (let [middleware (model-transform/wrap-model-transform hostname)
          wrapped    (middleware (constantly {:status 200
                                              :body   {:type     :tournament/tournament
                                                       :eid      tournament-eid
                                                       :name     "T"
                                                       :game-eid game-eid}}))
          response   (wrapped {:request-method :get :uri "/api/anywhere"})]
      (is (not (contains? (:body response) :_links))))))

(deftest route-without-response-schema-is-passed-through
  (testing "Matched route without :responses → body returned untouched"
    (let [body     {:any "thing"}
          response (run-middleware (str "/api/tournament/" tournament-eid "/no-schema")
                                   (constantly {:status 200 :body body}))]
      (is (= body (:body response))))))

(deftest non-map-body-is-passed-through
  (testing "Body that isn't a map (e.g. a string or seq) is left untouched"
    (let [response (run-middleware (str "/api/tournament/" tournament-eid "/resource")
                                   (constantly {:status 200 :body "raw payload"}))]
      (is (= "raw payload" (:body response))))))
