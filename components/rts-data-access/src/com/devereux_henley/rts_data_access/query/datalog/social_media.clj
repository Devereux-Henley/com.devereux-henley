(ns com.devereux-henley.rts-data-access.query.datalog.social-media
  "Datalevin reads for the social-media domain."
  (:require
   [datalevin.core :as d]))

(def ^:private platform-pattern
  [:social-media-platform/eid
   :social-media-platform/name
   :social-media-platform/description
   :social-media-platform/platform-url])

(defn- ->platform
  [m]
  (when m
    {:eid          (:social-media-platform/eid m)
     :name         (:social-media-platform/name m)
     :description  (:social-media-platform/description m)
     :platform-url (:social-media-platform/platform-url m)}))

(defn platform-by-eid
  "Fetch a social-media platform by eid. Returns nil when not found."
  [conn eid]
  (->platform (d/pull (d/db conn) platform-pattern [:social-media-platform/eid eid])))
