(ns com.devereux-henley.rts-data-access.query.replay
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract])
  (:import
   [java.sql Connection]))

(def get-replay-by-eid-query (resource/load-query-resource "replay" "get-replay-by-eid.sql"))

(defn get-replay-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/replay-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-replay-by-eid-query (str eid)] schema/replay-entity))

(defn create-replay
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] schema/create-replay-params]
                   schema/replay-entity])}
  [connection specification]
  (jdbc.contract/insert! connection :replay specification)
  (get-replay-by-eid connection (:eid specification)))
