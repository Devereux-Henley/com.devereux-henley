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
