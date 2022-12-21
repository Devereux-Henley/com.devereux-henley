(ns com.devereux-henley.rts-api.core
  (:require
   [com.devereux-henley.rts-api.system :as system]))

(defn -main [& args]
  (try
    (system/go!)
    (catch Exception exc
      (println "Oops")
      (println (str exc))
      (system/halt!)
      (System/exit 1))))
