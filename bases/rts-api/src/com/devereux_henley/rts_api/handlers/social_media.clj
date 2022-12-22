(ns com.devereux-henley.rts-api.handlers.social-media
  (:require
   [com.devereux-henley.rts-api.db.social-media :as db.social-media]
   [integrant.core]))

(defn get-platform-by-eid
  [dependencies eid]
  (assoc (db.social-media/get-platform-by-eid (:connection dependencies) eid) :type :social-media/platform))
