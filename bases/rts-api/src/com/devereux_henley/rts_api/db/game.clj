(ns com.devereux-henley.rts-api.db.game
  (:require
   [com.devereux-henley.rts-api.db.core :as db.core]
   [com.devereux-henley.schema.contract :as schema.contract]
   [clojure.java.io :as io])
  (:import
   [java.sql Connection]))

(def game-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def faction-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:game-eid :uuid]
    [:description {:min 1} :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def game-social-link-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:url :url]
    [:game-eid :uuid]
    [:social-media-platform-eid :uuid]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(defn get-game-by-eid
  [connection eid]
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   game-entity])}
  (db.core/entity-by-eid connection
                         :game
                         eid
                         game-entity))

(def game-query (slurp (io/resource "rts-api/sql/game/get-games.sql")))

(defn get-games
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection]]
                   [:sequential game-entity]])}
  [connection]
  (db.core/query-for-entities connection [game-query] game-entity))

(def get-faction-by-eid-query (slurp (io/resource "rts-api/sql/game/get-faction-by-eid.sql")))

(defn get-faction-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   faction-entity])}
  [connection eid]
  (db.core/query-for-entity connection [get-faction-by-eid-query eid] faction-entity))

(def get-factions-for-game-query (slurp (io/resource "rts-api/sql/game/get-factions-for-game.sql")))

(defn get-factions-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential faction-entity]])}
  [connection game-eid]
  (db.core/query-for-entities connection [get-factions-for-game-query game-eid] faction-entity))

(def get-socials-for-game-query (slurp (io/resource "rts-api/sql/game/get-socials-for-game.sql")))

(defn get-socials-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential game-social-link-entity]])}
  [connection game-eid]
  (db.core/query-for-entities connection
                              [get-socials-for-game-query game-eid]
                              game-social-link-entity))

(def get-game-social-link-by-eid-query (slurp
                                        (io/resource
                                         "rts-api/sql/game/get-game-social-link-by-eid.sql")))

(defn get-game-social-link-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   game-social-link-entity])}
  [connection eid]
  (db.core/query-for-entity connection
                            [get-game-social-link-by-eid-query eid]
                            game-social-link-entity))

(def unit-type-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:game-eid :uuid]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def unit-category-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:game-eid :uuid]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def unit-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:game-eid :uuid]
    [:unit-type-eid :uuid]
    [:unit-type-name :string]
    [:unit-category-eid :uuid]
    [:unit-category-name :string]
    [:cost [:maybe :int]]
    [:unit-statistics :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def get-unit-type-by-eid-query (slurp (io/resource "rts-api/sql/game/get-unit-type-by-eid.sql")))

(defn get-unit-type-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   unit-type-entity])}
  [connection eid]
  (db.core/query-for-entity connection [get-unit-type-by-eid-query eid] unit-type-entity))

(def get-unit-types-for-game-query (slurp (io/resource "rts-api/sql/game/get-unit-types-for-game.sql")))

(defn get-unit-types-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential unit-type-entity]])}
  [connection game-eid]
  (db.core/query-for-entities connection [get-unit-types-for-game-query game-eid] unit-type-entity))

(def get-unit-category-by-eid-query (slurp (io/resource "rts-api/sql/game/get-unit-category-by-eid.sql")))

(defn get-unit-category-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   unit-category-entity])}
  [connection eid]
  (db.core/query-for-entity connection [get-unit-category-by-eid-query eid] unit-category-entity))

(def get-unit-categories-for-game-query (slurp (io/resource "rts-api/sql/game/get-unit-categories-for-game.sql")))

(defn get-unit-categories-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential unit-category-entity]])}
  [connection game-eid]
  (db.core/query-for-entities connection [get-unit-categories-for-game-query game-eid] unit-category-entity))

(def get-unit-by-eid-query (slurp (io/resource "rts-api/sql/game/get-unit-by-eid.sql")))

(defn get-unit-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   unit-entity])}
  [connection eid]
  (db.core/query-for-entity connection [get-unit-by-eid-query eid] unit-entity))

(def get-units-for-game-query (slurp (io/resource "rts-api/sql/game/get-units-for-game.sql")))

(defn get-units-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential unit-entity]])}
  [connection game-eid]
  (db.core/query-for-entities connection [get-units-for-game-query game-eid] unit-entity))

(def get-units-for-faction-query (slurp (io/resource "rts-api/sql/game/get-units-for-faction.sql")))

(defn get-units-for-faction
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential unit-entity]])}
  [connection faction-eid]
  (db.core/query-for-entities connection [get-units-for-faction-query faction-eid] unit-entity))
