(ns com.devereux-henley.rose-api.handlers.flower
  (:require
   [com.devereux-henley.rose-api.db.flower :as db.flower]
   [integrant.core]))

(defn get-flower
  [dependencies id]
  (db.flower/get-flower-by-id (:connection dependencies) id))

(defn create-flower
  [dependencies create-specification]
  (db.flower/create-flower (:connection dependencies) create-specification))

(defn get-my-flower-collection
  [dependencies]
  [])

(defn get-recent-flower-collection
  [dependencies]
  [])
