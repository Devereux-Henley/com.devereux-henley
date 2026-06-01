(ns com.devereux-henley.e2e.playwright-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.content-negotiation.contract :as content-negotiation]
   [com.devereux-henley.datalog.contract :as datalog]
   [com.devereux-henley.e2e.contract :as e2e]
   [com.devereux-henley.http.contract :as http]
   [com.devereux-henley.resourcekit.contract :as resourcekit]
   [com.devereux-henley.rts-data-access.contract :as data-access]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.contract :as web]
   [com.devereux-henley.schema.contract :as schema]))

(def ^:private e2e-dir "components/e2e")
(def ^:private base-url "http://localhost:3001")
(def ^:private ci? (some? (System/getenv "CI")))

(def ^:private upstream-interfaces
  "Public vars from every brick interface that composes the running api server
   the Playwright suite drives. Referencing them here declares e2e's dependency
   on those bricks, so Polylith's incremental selection re-runs this suite when
   any of them changes."
  [#'content-negotiation/html-format
   #'datalog/get-conn
   #'http/handle-fetch-response
   #'resourcekit/asset-root
   #'data-access/game-entity
   #'domain/draft-resource
   #'web/view-routes
   #'schema/milliseconds-in-a-second])

(deftest upstream-stack-on-classpath
  (testing "every upstream brick interface the Playwright suite exercises resolves"
    (is (every? var? upstream-interfaces))))

(deftest playwright-e2e-tests
  (cond
    (not (e2e/playwright-available? e2e-dir))
    (if ci?
      (is false "Playwright not installed — npm ci + playwright install must run before poly test")
      (println "SKIP: Playwright not installed — run `npm install` in" e2e-dir))

    (not (e2e/server-reachable? base-url))
    (if ci?
      (is false "Dev server not reachable — start_dev_server.clj must run before poly test")
      (println "SKIP: Dev server not reachable at" base-url "— start it first"))

    :else
    (testing "All Playwright specs pass"
      (let [{:keys [exit out err]} (e2e/run-playwright e2e-dir)]
        (when-not (zero? exit)
          (println "--- Playwright stdout ---")
          (println out)
          (println "--- Playwright stderr ---")
          (println err))
        (is (zero? exit) "Playwright tests failed — see output above")))))
