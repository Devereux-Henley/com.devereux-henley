(ns com.devereux-henley.rts-api.http
  (:require
   [clj-http.client :as client]
   [clj-http.util :as util]
   [clojure.java.io :as io]
   [jsonista.core])
  (:import
   [java.io BufferedReader EOFException]))

;; For this file - see: https://github.com/dakrone/clj-http/blob/3.x/src/clj_http/client.clj
;; Extend clj-http to support jsonista.

(defn response-charset [response]
  (or (-> response :content-type-params :charset)
      "UTF-8"))

(defn can-parse-body? [{:keys [coerce] :as request} {:keys [status] :as _response}]
  (or (= coerce :always)
      (and (client/unexceptional-status-for-request? request status)
           (or (nil? coerce)
               (= coerce :unexceptional)))
      (and (not (client/unexceptional-status-for-request? request status))
           (= coerce :exceptional))))

(defn decode-json-body [body charset]
  (let [^BufferedReader br (io/reader (util/force-stream body) :encoding charset)]
    (try
      (.mark br 1)
      (let [first-char (int (try (.read br) (catch EOFException _ -1)))]
        (case first-char
          -1 nil
          (do (.reset br)
              (jsonista.core/read-value br (jsonista.core/object-mapper {:decode-key-fn true})))))
      (finally (.close br)))))

(defn coerce-json-body
  [request {:keys [body] :as response} & [charset]]
  (let [charset (or charset (response-charset response))
        body    (if (can-parse-body? request response)
               (decode-json-body body charset)
               (util/force-string body charset))]
    (assoc response :body body)))

(defmethod client/coerce-response-body :jsonista [request response]
  (coerce-json-body request response))
