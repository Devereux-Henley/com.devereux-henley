(ns com.devereux-henley.rts-data-access.query.game
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract])
  (:import
   [java.sql Connection]))

(def game-query (resource/load-query-resource "game" "get-games.sql"))

(def get-faction-by-eid-query (resource/load-query-resource "game" "get-faction-by-eid.sql"))

(def get-factions-for-game-query (resource/load-query-resource "game" "get-factions-for-game.sql"))

(def get-socials-for-game-query (resource/load-query-resource "game" "get-socials-for-game.sql"))

(def get-game-social-link-by-eid-query (resource/load-query-resource "game" "get-game-social-link-by-eid.sql"))

(def get-unit-type-by-eid-query (resource/load-query-resource "game" "get-unit-type-by-eid.sql"))

(def get-unit-types-for-game-query (resource/load-query-resource "game" "get-unit-types-for-game.sql"))

(def get-unit-category-by-eid-query (resource/load-query-resource "game" "get-unit-category-by-eid.sql"))

(def get-unit-categories-for-game-query (resource/load-query-resource "game" "get-unit-categories-for-game.sql"))

(def get-unit-by-eid-query (resource/load-query-resource "game" "get-unit-by-eid.sql"))

(def get-units-for-game-query (resource/load-query-resource "game" "get-units-for-game.sql"))

(def get-units-for-faction-query (resource/load-query-resource "game" "get-units-for-faction.sql"))

(defn get-game-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/game-entity])}
  [connection eid]
  (jdbc.contract/entity-by-eid connection :game eid schema/game-entity))

(defn get-games
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection]]
                   [:sequential schema/game-entity]])}
  [connection]
  (jdbc.contract/query-for-entities connection [game-query] schema/game-entity))

(defn get-faction-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/faction-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-faction-by-eid-query eid] schema/faction-entity))

(defn get-factions-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/faction-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-factions-for-game-query game-eid] schema/faction-entity))

(defn get-socials-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/game-social-link-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-socials-for-game-query game-eid] schema/game-social-link-entity))

(defn get-game-social-link-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/game-social-link-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-game-social-link-by-eid-query eid] schema/game-social-link-entity))

(defn get-unit-type-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/unit-type-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-unit-type-by-eid-query eid] schema/unit-type-entity))

(defn get-unit-types-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/unit-type-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-unit-types-for-game-query game-eid] schema/unit-type-entity))

(defn get-unit-category-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/unit-category-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-unit-category-by-eid-query eid] schema/unit-category-entity))

(defn get-unit-categories-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/unit-category-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-unit-categories-for-game-query game-eid] schema/unit-category-entity))

(defn get-unit-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/unit-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-unit-by-eid-query eid] schema/unit-entity))

(defn get-units-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/unit-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-units-for-game-query game-eid] schema/unit-entity))

(defn get-units-for-faction
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/unit-entity]])}
  [connection faction-eid]
  (jdbc.contract/query-for-entities connection [get-units-for-faction-query faction-eid] schema/unit-entity))
