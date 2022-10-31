(ns com.devereux-henley.rose-api.core
  (:require
   [com.devereux-henley.rose-api.system :as system]))

(defn -main [& args]
  (try
    (system/go!)
    (catch Exception exc
      (println "Oops")
      (println (str exc))
      (system/halt!)
      (System/exit 1))))
