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

;; ─── Entry queries ───────────────────────────────────────────────────────────

(def create-entry-query (resource/load-query-resource "tournament" "create-entry.sql"))

(def delete-entry-query (resource/load-query-resource "tournament" "delete-entry.sql"))

(def get-entries-for-tournament-query (resource/load-query-resource "tournament" "get-entries-for-tournament.sql"))

(def get-entry-by-tournament-and-player-query (resource/load-query-resource "tournament" "get-entry-by-tournament-and-player.sql"))

(defn create-entry
  [connection tournament-eid player-sub]
  (let [eid (str (random-uuid))]
    (jdbc.contract/execute-one!
     connection
     [create-entry-query eid player-sub (str (Instant/now)) tournament-eid])
    (jdbc.contract/query-for-entity
     connection
     [get-entry-by-tournament-and-player-query tournament-eid player-sub]
     schema/tournament-entry-entity)))

(defn delete-entry
  [connection tournament-eid player-sub]
  (jdbc.contract/execute-one!
   connection
   [delete-entry-query (str (Instant/now)) tournament-eid player-sub]))

(defn get-entries-for-tournament
  [connection tournament-eid]
  (jdbc.contract/query-for-entities
   connection
   [get-entries-for-tournament-query tournament-eid]
   schema/tournament-entry-entity))

(defn get-entry-by-tournament-and-player
  [connection tournament-eid player-sub]
  (jdbc.contract/query-for-entity
   connection
   [get-entry-by-tournament-and-player-query tournament-eid player-sub]
   schema/tournament-entry-entity))

;; ─── Match queries ───────────────────────────────────────────────────────────

(def create-match-query (resource/load-query-resource "tournament" "create-match.sql"))

(def get-match-by-eid-query (resource/load-query-resource "tournament" "get-match-by-eid.sql"))

(def get-matches-for-tournament-query (resource/load-query-resource "tournament" "get-matches-for-tournament.sql"))

(def get-matches-for-round-query (resource/load-query-resource "tournament" "get-matches-for-round.sql"))

(def update-match-result-query (resource/load-query-resource "tournament" "update-match-result.sql"))

(defn create-match
  [connection tournament-eid match-spec]
  (let [eid (str (random-uuid))
        now (str (Instant/now))]
    (jdbc.contract/execute-one!
     connection
     [create-match-query
      eid
      (:phase-index match-spec)
      (:round-index match-spec)
      (or (:bracket-type match-spec) "winners")
      (:player-one-sub match-spec)
      (:player-two-sub match-spec)
      (or (:format match-spec) 1)
      now now
      tournament-eid])
    (jdbc.contract/query-for-entity
     connection
     [get-match-by-eid-query eid]
     schema/match-entity)))

(defn get-match-by-eid
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-match-by-eid-query eid] schema/match-entity))

(defn get-matches-for-tournament
  [connection tournament-eid]
  (jdbc.contract/query-for-entities connection [get-matches-for-tournament-query tournament-eid] schema/match-entity))

(defn get-matches-for-round
  [connection tournament-eid phase-index round-index]
  (jdbc.contract/query-for-entities
   connection
   [get-matches-for-round-query tournament-eid phase-index round-index]
   schema/match-entity))

(defn update-match-result
  [connection match-eid winner-sub]
  (jdbc.contract/execute-one!
   connection
   [update-match-result-query winner-sub (str (Instant/now)) match-eid]))

;; ─── Game queries ────────────────────────────────────────────────────────────

(def create-game-query (resource/load-query-resource "tournament" "create-game.sql"))

(def get-games-for-match-query (resource/load-query-resource "tournament" "get-games-for-match.sql"))

(defn create-game
  [connection match-eid game-index winner-sub]
  (let [eid (str (random-uuid))]
    (jdbc.contract/execute-one!
     connection
     [create-game-query eid game-index winner-sub (str (Instant/now)) (str match-eid)])
    {:eid (java.util.UUID/fromString eid) :match-eid match-eid :game-index game-index :winner-sub winner-sub}))

(defn get-games-for-match
  [connection match-eid]
  (jdbc.contract/query-for-entities connection [get-games-for-match-query (str match-eid)] schema/match-game-entity))

;; ─── Tournament CRUD ─────────────────────────────────────────────────────────

(defn create-tournament
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] schema/create-tournament-params]
                   schema/tournament-entity])}
  [connection specification]
  (let [game      (jdbc.contract/entity-by-eid connection :game (:game-eid specification) schema/game-entity)
        league-id (when-let [leid (:league-eid specification)]
                    (:id (jdbc.contract/entity-by-eid connection :league leid schema/league-entity)))
        season-id (when-let [seid (:season-eid specification)]
                    (:id (jdbc.contract/entity-by-eid connection :season seid schema/season-entity)))]
    (jdbc/with-transaction [tx connection]
      (jdbc.contract/insert! tx
                             :tournament
                             (-> specification
                                 (dissoc :game-eid :league-eid :season-eid)
                                 (assoc :game-id (:id game))
                                 (cond-> league-id (assoc :league-id league-id))
                                 (cond-> season-id (assoc :season-id season-id))))
      (get-tournament-by-eid connection (:eid specification)))))

;; ─── Match draft assignment ─────────────────────────────────────────────────

(def set-match-player-one-draft-query
  (resource/load-query-resource "tournament" "set-match-player-one-draft.sql"))

(def set-match-player-two-draft-query
  (resource/load-query-resource "tournament" "set-match-player-two-draft.sql"))

(defn set-match-player-one-draft
  [connection match-eid draft-eid]
  (jdbc.contract/execute-one!
   connection
   [set-match-player-one-draft-query (str draft-eid) (str (Instant/now)) (str match-eid)]))

(defn set-match-player-two-draft
  [connection match-eid draft-eid]
  (jdbc.contract/execute-one!
   connection
   [set-match-player-two-draft-query (str draft-eid) (str (Instant/now)) (str match-eid)]))
