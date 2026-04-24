(ns com.devereux-henley.rts-data-access.query.season
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract]
   [next.jdbc :as jdbc])
  (:import
   [java.sql Connection]))

(def get-season-by-eid-query (resource/load-query-resource "season" "get-season-by-eid.sql"))

(def get-seasons-for-league-query (resource/load-query-resource "season" "get-seasons-for-league.sql"))

(def get-current-season-for-league-query (resource/load-query-resource "season" "get-current-season-for-league.sql"))

(def get-max-ordinal-for-league-query (resource/load-query-resource "season" "get-max-ordinal-for-league.sql"))

(defn get-season-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/season-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-season-by-eid-query eid] schema/season-entity))

(defn get-seasons-for-league
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/season-entity]])}
  [connection league-eid]
  (jdbc.contract/query-for-entities connection [get-seasons-for-league-query league-eid] schema/season-entity))

(defn get-current-season-for-league
  [connection league-eid]
  (jdbc.contract/query-for-entity connection [get-current-season-for-league-query league-eid] schema/season-entity))

(defn get-max-ordinal-for-league
  [connection league-eid]
  (or (jdbc.contract/query-for-entity connection [get-max-ordinal-for-league-query league-eid] schema/max-ordinal-entity)
      {:max-ordinal 0}))

(defn create-season
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] schema/create-season-params]
                   schema/season-entity])}
  [connection specification]
  (let [league (jdbc.contract/entity-by-eid connection :league (:league-eid specification) schema/league-entity)]
    (jdbc/with-transaction [tx connection]
      (jdbc.contract/insert! tx
                             :season
                             (-> specification
                                 (dissoc :league-eid)
                                 (assoc :league-id (:id league))))
      (get-season-by-eid connection (:eid specification)))))
