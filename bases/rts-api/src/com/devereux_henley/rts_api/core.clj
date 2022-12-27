(ns com.devereux-henley.rts-api.core
  (:require
   [com.devereux-henley.rts-api.system :as system]
   [taoensso.timbre :as log]))

(defn -main [& args]
  (try
    (system/go!)
    (catch Exception exc
      (log/error exc "System exiting.")
      (system/halt!)
      (System/exit 1))))
