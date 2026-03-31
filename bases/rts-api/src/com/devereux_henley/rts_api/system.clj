(ns com.devereux-henley.rts-api.system
  (:require
   [com.devereux-henley.rts-api.configuration :as configuration]
   [com.devereux-henley.rts-web.contract :as rts-web]
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
  "Clears the Selmer template cache and busts the CSS cache. Call from the REPL
  during local development to pick up changes to HTML templates and CSS without
  restarting the system."
  []
  (reset! selmer.parser/templates {})
  (reset! rts-web/css-version (System/currentTimeMillis)))

(comment
  (go!)
  (halt!)
  (restart!)
  (reload-views!)
  (get-system))
