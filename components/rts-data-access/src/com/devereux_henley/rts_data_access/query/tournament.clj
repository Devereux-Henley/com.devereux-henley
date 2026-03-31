(ns com.devereux-henley.rts-data-access.query.tournament
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.query.game :as query.game]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract]
   [jsonista.core :as jsonista]
   [next.jdbc :as jdbc])
  (:import
   [java.sql Connection]
   [java.time Instant]))

(def get-tournaments-query (resource/load-query-resource "tournament" "get-tournaments.sql"))

(def get-tournament-by-eid-query
  (resource/load-query-resource "tournament" "get-tournament-by-eid.sql"))

(def get-tournament-snapshot-by-eid-query
  (resource/load-query-resource "tournament" "get-tournament-snapshot-by-eid.sql"))

(def get-tournament-snapshot-by-tournament-eid-query
  (resource/load-query-resource "tournament" "get-tournament-snapshot-by-tournament-eid.sql"))

(def update-tournament-snapshot-by-tournament-eid-query
  (resource/load-query-resource "tournament" "update-tournament-snapshot-by-tournament-eid.sql"))

(defn get-tournaments
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :instant :pos-int :pos-int]
                   [:sequential schema/tournament-entity]])}
  [connection since size offset]
  (jdbc.contract/query-for-entities connection
                                    [get-tournaments-query since size offset]
                                    schema/tournament-entity))

(defn get-tournaments-by-game-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid :instant :pos-int :pos-int]
                   [:sequential schema/tournament-entity]])}
  [connection game-eid since size offset]
  ;; TODO Implement paged tournament list.
  [])

(defn get-tournament-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/tournament-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection
                                  [get-tournament-by-eid-query eid]
                                  schema/tournament-entity))

(defn get-tournament-snapshot-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/tournament-snapshot-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection
                                  [get-tournament-snapshot-by-eid-query eid]
                                  schema/tournament-snapshot-entity))

(defn get-tournament-snapshot-by-tournament-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/tournament-snapshot-entity])}
  [connection tournament-eid]
  (jdbc.contract/query-for-entity connection
                                  [get-tournament-snapshot-by-tournament-eid-query tournament-eid]
                                  schema/tournament-snapshot-entity))

(defn create-tournament
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] schema/create-tournament-params]
                   schema/tournament-entity])}
  [connection specification]
  (let [game (query.game/get-game-by-eid connection (:game-eid specification))]
    (jdbc/with-transaction [tx connection]
      (jdbc.contract/insert! tx
                             :tournament
                             (-> specification
                                 (dissoc :game-eid)
                                 (assoc :game-id (:id game))))
      (let [tournament (get-tournament-by-eid connection (:eid specification))]
        (jdbc.contract/insert! tx
                               :tournament_snapshot
                               {:eid              (random-uuid)
                                :tournament-id    (:id tournament)
                                :tournament-state (jsonista/write-value-as-string {:type   (:tournament-type specification)
                                                                                   :rounds []})
                                :version          1
                                :created-by-sub   (:created-by-sub specification)
                                :created-at       (Instant/now)
                                :updated-at       (Instant/now)})
        tournament))))

(defn update-tournament-snapshot-by-tournament-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid schema/update-tournament-snapshot-specification]
                   schema/tournament-snapshot-entity])}
  [connection tournament-eid new-snapshot]
  (jdbc.contract/execute-one! connection
                              [update-tournament-snapshot-by-tournament-eid-query
                               (:tournament-state new-snapshot)
                               (:update-at new-snapshot)
                               tournament-eid])
  (get-tournament-snapshot-by-tournament-eid connection tournament-eid))
