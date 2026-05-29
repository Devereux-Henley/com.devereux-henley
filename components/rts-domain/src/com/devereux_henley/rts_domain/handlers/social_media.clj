(ns com.devereux-henley.rts-domain.handlers.social-media
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]))

(defn get-platform-by-eid
  "Fetches a social-media platform by eid and tags it with
  `:type :social-media/platform`. Returns nil when not found."
  [dependencies eid]
  (some-> (db/platform-by-eid (:datalog-connection dependencies) eid)
          (assoc :type :social-media/platform)))
