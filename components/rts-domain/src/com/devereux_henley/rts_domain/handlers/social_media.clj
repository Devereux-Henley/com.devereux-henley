(ns com.devereux-henley.rts-domain.handlers.social-media
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]))

(defn get-platform-by-eid
  [dependencies eid]
  (assoc (db/get-platform-by-eid (:connection dependencies) eid) :type :social-media/platform))
