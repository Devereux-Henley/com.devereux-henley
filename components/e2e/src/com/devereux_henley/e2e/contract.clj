(ns com.devereux-henley.e2e.contract
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell])
  (:import
   [java.net HttpURLConnection URL]))

(defn playwright-available?
  "Returns true when npx is on the PATH and Playwright's node_modules are installed
   under the e2e component directory."
  [e2e-dir]
  (and (.isDirectory (io/file e2e-dir "node_modules"))
       (zero? (:exit (shell/sh "which" "npx")))))

(defn server-reachable?
  "Returns true when the application server responds at the given base URL."
  [base-url]
  (try
    (let [conn (-> (URL. (str base-url "/status"))
                   (.openConnection))]
      (.setConnectTimeout ^HttpURLConnection conn 2000)
      (.setReadTimeout ^HttpURLConnection conn 2000)
      (.setRequestMethod ^HttpURLConnection conn "GET")
      (let [code (.getResponseCode ^HttpURLConnection conn)]
        (.disconnect ^HttpURLConnection conn)
        (< code 500)))
    (catch Exception _ false)))

(defn run-playwright
  "Runs Playwright tests from inside the e2e component directory so npx resolves
   the local node_modules. Returns the shell result map."
  [e2e-dir]
  (shell/sh "npx" "playwright" "test" :dir e2e-dir))

(defn shutdown-server
  "Posts to the dev-only /shutdown endpoint for a clean Integrant halt."
  [base-url]
  (try
    (let [conn (-> (URL. (str base-url "/shutdown"))
                   (.openConnection))]
      (.setConnectTimeout ^HttpURLConnection conn 2000)
      (.setReadTimeout ^HttpURLConnection conn 2000)
      (.setRequestMethod ^HttpURLConnection conn "POST")
      (.setDoOutput ^HttpURLConnection conn true)
      (let [code (.getResponseCode ^HttpURLConnection conn)]
        (.disconnect ^HttpURLConnection conn)
        (< code 500)))
    (catch Exception _ false)))

;;; ─── Datalog per-run isolation ─────────────────────────────────────────────
;;; Datalevin stores state in an LMDB directory. Stale state across runs
;;; corrupts assertions, so the orchestration (CI script or REPL runner)
;;; allocates a fresh dir per test run, exports it as `DATALEVIN_DB_DIR`
;;; before starting the dev server, and deletes it on teardown.

(defn make-datalevin-test-dir!
  "Create and return a unique Datalevin directory for this test run, suitable
   for export as `DATALEVIN_DB_DIR` before launching the dev server. Default
   parent is `java.io.tmpdir`; pass `parent` to override."
  ([] (make-datalevin-test-dir! (System/getProperty "java.io.tmpdir")))
  ([parent]
   (let [dir (io/file parent (str "datalevin-e2e-" (random-uuid)))]
     (.mkdirs dir)
     (.getAbsolutePath dir))))

(defn cleanup-datalevin-test-dir!
  "Recursively delete a Datalevin test directory. Tolerates a missing path so
   the teardown step stays idempotent."
  [path]
  (let [root (io/file path)]
    (when (.exists root)
      (doseq [child (reverse (file-seq root))]
        (.delete ^java.io.File child)))))
