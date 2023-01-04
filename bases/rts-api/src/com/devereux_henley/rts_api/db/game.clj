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
