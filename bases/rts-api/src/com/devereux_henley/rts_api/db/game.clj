(ns com.devereux-henley.rts-api.db.game
  (:require
   [com.devereux-henley.rts-api.db.result-set :as db.result-set]
   [com.devereux-henley.schema.contract :as schema]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as jdbc.sql]
   [clojure.java.io :as io])
  (:import
   [java.sql Connection]))

(def game-entity
  [:map
   [:id :int]
   [:eid :uuid]
   [:name {:min 1} :string]
   [:description {:min 1} :string]
   [:version :int]
   [:created-at :instant]
   [:updated-at :instant]
   [:deleted-at [:maybe :instant]]])

(def game-social-link-entity
  [:map
   [:id :int]
   [:eid :uuid]
   [:url :url]
   [:game-eid :uuid]
   [:social-media-platform-eid :uuid]
   [:version :int]
   [:created-at :instant]
   [:updated-at :instant]
   [:deleted-at [:maybe :instant]]])

(defn get-game-by-eid
  [connection eid]
  {:malli/schema (schema/to-schema [:=> [:cat [:instance Connection] :uuid] game-entity])}
  (jdbc.sql/get-by-id connection :game eid :eid db.result-set/default-options))

(def game-query (slurp (io/resource "rts-api/sql/game/get-games.sql")))

(defn get-games
  {:malli/schema (schema/to-schema [:=> [:cat [:instance Connection]] [:sequential game-entity]])}
  [connection]
  (jdbc.sql/query connection [game-query] db.result-set/default-options))

(def get-socials-for-game-query (slurp (io/resource "rts-api/sql/game/get-socials-for-game.sql")))

(defn get-socials-for-game
  {:malli/schema (schema/to-schema [:=>
                                    [:cat [:instance Connection] :uuid]
                                    [:sequential game-social-link-entity]])}
  [connection game-eid]
  (jdbc.sql/query connection [get-socials-for-game-query game-eid] db.result-set/default-options))

(def get-game-social-link-by-eid-query (slurp
                                        (io/resource
                                         "rts-api/sql/game/get-game-social-link-by-eid.sql")))

(defn get-game-social-link-by-eid
  {:malli/schema (schema/to-schema [:=>
                                    [:cat [:instance Connection] :uuid]
                                    [:sequential game-social-link-entity]])}
  [connection eid]
  (jdbc.sql/query connection [get-game-social-link-by-eid-query eid] db.result-set/default-options))
