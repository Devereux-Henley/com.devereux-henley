(ns com.devereux-henley.rts-data-access.query.stats
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract])
  (:import
   [java.sql Connection]))

(def get-faction-standings-for-game-query
  (resource/load-query-resource "stats" "get-faction-standings-for-game.sql"))

(def get-faction-standings-for-league-query
  (resource/load-query-resource "stats" "get-faction-standings-for-league.sql"))

(def get-faction-standings-for-season-query
  (resource/load-query-resource "stats" "get-faction-standings-for-season.sql"))

(defn get-faction-standings-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/faction-standings-row-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities
   connection
   [get-faction-standings-for-game-query game-eid game-eid]
   schema/faction-standings-row-entity))

(defn get-faction-standings-for-league
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/faction-standings-row-entity]])}
  [connection league-eid]
  (jdbc.contract/query-for-entities
   connection
   [get-faction-standings-for-league-query league-eid league-eid]
   schema/faction-standings-row-entity))

(defn get-faction-standings-for-season
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/faction-standings-row-entity]])}
  [connection season-eid]
  (jdbc.contract/query-for-entities
   connection
   [get-faction-standings-for-season-query season-eid season-eid]
   schema/faction-standings-row-entity))
