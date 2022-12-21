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

(defn get-game-by-id
  [connection eid]
  {:malli/schema (schema/to-schema [:=> [:cat [:instance Connection] :uuid] game-entity])}
  (jdbc.sql/get-by-id connection :game eid :eid {:builder-fn db.result-set/default-builder}))

(def game-query (slurp (io/resource "rts-api/sql/game/get-games.sql")))

(defn get-games
  {:malli/schema (schema/to-schema [:=> [:cat [:instance Connection]] [:sequential game-entity]])}
  [connection]
  (jdbc.sql/query connection [game-query] {:builder-fn db.result-set/default-builder}))
