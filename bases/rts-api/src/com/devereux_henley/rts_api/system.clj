(ns com.devereux-henley.rts-api.system
  (:require
   [com.devereux-henley.rts-api.configuration :as configuration]
   [integrant.core]
   [selmer.parser]))

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

(defn reload-views!
  "Clears the Selmer template cache. Call from the REPL during local development
  to pick up changes to HTML templates without restarting the system."
  []
  (reset! selmer.parser/templates {}))

(comment
  (go!)
  (halt!)
  (restart!)
  (reload-views!)
  (get-system))
