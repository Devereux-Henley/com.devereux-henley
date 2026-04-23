(ns com.devereux-henley.rts-data-access.query.league
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract]
   [next.jdbc :as jdbc])
  (:import
   [java.sql Connection]
   [java.time Instant]))

(def get-league-by-eid-query (resource/load-query-resource "league" "get-league-by-eid.sql"))

(def get-leagues-for-game-query (resource/load-query-resource "league" "get-leagues-for-game.sql"))

(def update-league-query (resource/load-query-resource "league" "update-league.sql"))

(defn get-league-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/league-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-league-by-eid-query eid] schema/league-entity))

(defn get-leagues-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/league-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-leagues-for-game-query game-eid] schema/league-entity))

(defn create-league
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] schema/create-league-params]
                   schema/league-entity])}
  [connection specification]
  (let [game (jdbc.contract/entity-by-eid connection :game (:game-eid specification) schema/game-entity)]
    (jdbc/with-transaction [tx connection]
      (jdbc.contract/insert! tx
                             :league
                             (-> specification
                                 (dissoc :game-eid)
                                 (assoc :game-id (:id game))))
      (get-league-by-eid connection (:eid specification)))))

(defn update-league
  [connection league-eid {:keys [name description]}]
  (jdbc.contract/execute-one!
   connection
   [update-league-query name description (str (Instant/now)) (str league-eid)])
  (get-league-by-eid connection league-eid))
