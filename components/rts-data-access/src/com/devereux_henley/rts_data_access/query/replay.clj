(ns com.devereux-henley.rts-data-access.query.replay
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract])
  (:import
   [java.sql Connection]
   [java.time Instant]))

(def get-replay-by-eid-query (resource/load-query-resource "replay" "get-replay-by-eid.sql"))
(def get-replays-for-uploader-query (resource/load-query-resource "replay" "get-replays-for-uploader.sql"))
(def update-replay-winner-query (resource/load-query-resource "replay" "update-replay-winner.sql"))

(defn get-replay-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/replay-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-replay-by-eid-query eid] schema/replay-entity))

(defn get-replays-for-uploader
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :string]
                   [:sequential schema/replay-entity]])}
  [connection uploader-sub]
  (jdbc.contract/query-for-entities connection [get-replays-for-uploader-query uploader-sub] schema/replay-entity))

(defn create-replay
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] schema/create-replay-params]
                   schema/replay-entity])}
  [connection specification]
  (jdbc.contract/insert! connection :replay specification)
  (get-replay-by-eid connection (:eid specification)))

(defn update-replay-winner
  "Sets the user-declared winning alliance index (or -1 for draw, nil to clear)."
  [connection eid winning-alliance-idx]
  (jdbc.contract/execute-one!
   connection
   [update-replay-winner-query winning-alliance-idx (str (Instant/now)) (str eid)])
  (get-replay-by-eid connection eid))
