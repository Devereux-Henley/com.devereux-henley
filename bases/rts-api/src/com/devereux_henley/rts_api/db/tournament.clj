(ns com.devereux-henley.rts-api.db.tournament
  (:require
   [com.devereux-henley.rts-api.db.core :as db.core]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc.result-set]
   [next.jdbc.sql :as jdbc.sql]
   [clojure.java.io :as io])
  (:import
   [java.sql Connection]))

(def tournament-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:game-eid :uuid]
    [:title :string]
    [:description :string]
    [:tournament-start-datetime :instant]
    [:tournament-checkin-datetime :instant]
    [:tournament-type [:enum "elimination" "round-robin"]]
    [:competitor-type [:enum "player"]]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def tournament-snapshot-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:tournament-eid :uuid]
    [:tournament-state :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(defn load-tournament-query
  [file-name]
  (slurp (io/resource (str "rts-api/sql/tournament/" file-name))))

(defn get-tournaments
  [connection since size])

(def get-tournament-by-eid-query
  (load-tournament-query "get-tournament-by-eid.sql"))

(defn get-tournament-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   tournament-entity])}
  [connection eid]
  (db.core/query-for-entity connection
                            [get-tournament-by-eid-query eid]
                            tournament-entity))

(defn create-tournament
  [connection create-specification])

(defn get-tournament-snapshot-by-tournament-eid
  [connection tournament-eid])

(defn update-tournament-snapshot-by-tournament-eid
  [connection tournament-eid new-snapshot])
