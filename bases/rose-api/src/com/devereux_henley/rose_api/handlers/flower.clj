(ns com.devereux-henley.rose-api.handlers.flower
  (:require
   [com.devereux-henley.rose-api.db.flower :as db.flower]
   [integrant.core]))

(defn get-flower-by-eid
  [dependencies eid]
  (db.flower/get-flower-by-eid (:connection dependencies) eid))

(defn create-flower
  [dependencies create-specification]
  (db.flower/create-flower (:connection dependencies) create-specification))

(defn get-flowers-by-user-eid
  [dependencies user_eid]
  (db.flower/get-flowers-by-user-eid (:connection dependencies) user_eid))

(defn get-recent-flower-collection
  [dependencies]
  [])
