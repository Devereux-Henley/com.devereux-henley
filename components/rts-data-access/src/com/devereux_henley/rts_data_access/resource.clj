(ns com.devereux-henley.rts-data-access.resource
  (:require
   [clojure.java.io :as io]))

(defn load-query-resource
  [domain file-name]
  (slurp (io/resource (str "rts-data-access/sql/" domain "/" file-name))))
