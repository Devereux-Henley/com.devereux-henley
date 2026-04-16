(ns com.devereux-henley.rts-data-access.query.tournament
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract]
   [next.jdbc :as jdbc])
  (:import
   [java.sql Connection]
   [java.time Instant]))

(def get-tournament-by-eid-query (resource/load-query-resource "tournament" "get-tournament-by-eid.sql"))

(def get-tournaments-for-game-query (resource/load-query-resource "tournament" "get-tournaments-for-game.sql"))

(def get-tournament-state-query (resource/load-query-resource "tournament" "get-tournament-state.sql"))

(def upsert-tournament-state-query (resource/load-query-resource "tournament" "upsert-tournament-state.sql"))

(defn get-tournament-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/tournament-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-tournament-by-eid-query eid] schema/tournament-entity))

(defn get-tournaments-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/tournament-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-tournaments-for-game-query game-eid] schema/tournament-entity))

(defn get-tournament-state
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/tournament-state-entity])}
  [connection tournament-eid]
  (jdbc.contract/query-for-entity connection [get-tournament-state-query tournament-eid] schema/tournament-state-entity))

(defn upsert-tournament-state
  [connection tournament-eid state-json-str]
  (let [tournament (get-tournament-by-eid connection tournament-eid)]
    (jdbc.contract/execute-one!
     connection
     [upsert-tournament-state-query (:id tournament) state-json-str (str (Instant/now))])))

;; ─── Registration queries ────────────────────────────────────────────────────

(def register-player-query (resource/load-query-resource "tournament" "register-player.sql"))

(def withdraw-player-query (resource/load-query-resource "tournament" "withdraw-player.sql"))

(def get-registrations-for-tournament-query (resource/load-query-resource "tournament" "get-registrations-for-tournament.sql"))

(def get-registration-by-tournament-and-player-query (resource/load-query-resource "tournament" "get-registration-by-tournament-and-player.sql"))

(defn register-player
  [connection tournament-eid player-sub]
  (let [eid (str (random-uuid))]
    (jdbc.contract/execute-one!
     connection
     [register-player-query eid player-sub (str (Instant/now)) tournament-eid])
    (jdbc.contract/query-for-entity
     connection
     [get-registration-by-tournament-and-player-query tournament-eid player-sub]
     schema/tournament-registration-entity)))

(defn withdraw-player
  [connection tournament-eid player-sub]
  (jdbc.contract/execute-one!
   connection
   [withdraw-player-query (str (Instant/now)) tournament-eid player-sub]))

(defn get-registrations-for-tournament
  [connection tournament-eid]
  (jdbc.contract/query-for-entities
   connection
   [get-registrations-for-tournament-query tournament-eid]
   schema/tournament-registration-entity))

(defn get-registration-by-tournament-and-player
  [connection tournament-eid player-sub]
  (jdbc.contract/query-for-entity
   connection
   [get-registration-by-tournament-and-player-query tournament-eid player-sub]
   schema/tournament-registration-entity))

;; ─── Tournament CRUD ─────────────────────────────────────────────────────────

(defn create-tournament
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] schema/create-tournament-params]
                   schema/tournament-entity])}
  [connection specification]
  (let [game (jdbc.contract/entity-by-eid connection :game (:game-eid specification) schema/game-entity)]
    (jdbc/with-transaction [tx connection]
      (jdbc.contract/insert! tx
                             :tournament
                             (-> specification
                                 (dissoc :game-eid)
                                 (assoc :game-id (:id game))))
      (get-tournament-by-eid connection (:eid specification)))))
