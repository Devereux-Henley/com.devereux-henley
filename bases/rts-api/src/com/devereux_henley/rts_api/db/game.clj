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

(defn get-socials-for-game
  {:malli/schema (schema/to-schema [:=>
                                    [:cat [:instance Connection] :uuid]
                                    [:sequential game-social-link-entity]])}
  [connection game-eid]
  (if-let [game (get-game-by-eid connection game-eid)]
    (jdbc.sql/find-by-keys
     connection
     :game_social_link
     {:game_id (:id game)}
     db.result-set/default-options)
    []))

(defn get-game-social-link-by-eid
  {:malli/schema (schema/to-schema [:=>
                                    [:cat [:instance Connection] :uuid]
                                    [:sequential game-social-link-entity]])}
  [connection eid]
  (jdbc.sql/get-by-id
   connection
   :game_social_link
   eid
   :eid
   db.result-set/default-options))
