(ns com.devereux-henley.e2e.playwright-test
  ;; The Playwright suite drives the running api server over HTTP, so it touches
  ;; no upstream brick var directly. These requires exist purely so Polylith
  ;; records e2e's dependency on every brick composing that server and re-runs
  ;; the suite when any of them changes; unused-namespace is off for this reason.
  {:clj-kondo/config '{:linters {:unused-namespace {:level :off}}}}
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.content-negotiation.contract]
   [com.devereux-henley.e2e.contract :as e2e]
   [com.devereux-henley.http.contract]
   [com.devereux-henley.resourcekit.contract]
   [com.devereux-henley.rts-data-access.contract]
   [com.devereux-henley.rts-domain.contract]
   [com.devereux-henley.rts-web.contract]
   [com.devereux-henley.schema.contract]))

(def ^:private e2e-dir "components/e2e")
(def ^:private base-url "http://localhost:3001")
(def ^:private ci? (some? (System/getenv "CI")))

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
