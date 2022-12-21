(ns com.devereux-henley.rts-api.system
  (:require
   [com.devereux-henley.rts-api.configuration :as configuration]
   [integrant.core]))

(defonce ^:private system (atom {}))

(defn go! []
  (reset! system (-> configuration/core-configuration
                     integrant.core/prep
                     integrant.core/init)))

(defn halt! []
  (when-some [live-system @system]
    (integrant.core/halt! live-system)))

(defn restart! []
  (halt!)
  (go!))

(defn get-system [] @system)

(comment
  (go!)
  (halt!)
  (restart!)
  (get-system))
