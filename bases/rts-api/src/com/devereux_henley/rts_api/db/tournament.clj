(ns com.devereux-henley.rts-api.db.tournament
  (:require
   [com.devereux-henley.rts-api.db.core :as db.core]
   [com.devereux-henley.rts-api.db.game :as db.game]
   [com.devereux-henley.schema.contract :as schema.contract]
   [jsonista.core :as jsonista]
   [next.jdbc :as jdbc])
  (:import
   [java.sql Connection]
   [java.time Instant]))

(def create-tournament-specification
  (schema.contract/to-schema
   [:map
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
    [:updated-at :instant]]))

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

(def update-tournament-snapshot-specification
  (schema.contract/to-schema
   [:map
    [:tournament-state :string]
    [:updated-at :instant]]))

(def load-tournament-query (partial db.core/load-query-resource "tournament"))

(def get-tournaments-query
  (load-tournament-query "get-tournaments.sql"))

(defn get-tournaments
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :instant :pos-int :pos-int]
                   [:sequential tournament-entity]])}
  [connection since size offset]
  (db.core/query-for-entities connection
                              [get-tournaments-query since size offset]
                              tournament-entity))

(defn get-tournaments-by-game-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid :instant :pos-int :pos-int]
                   [:sequential tournament-entity]])}
  [connection game-eid since size offset]
  ;; TODO Implement paged tournament list.
  [])

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

(def get-tournament-snapshot-by-eid-query
  (load-tournament-query "get-tournament-snapshot-by-eid.sql"))

(defn get-tournament-snapshot-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   tournament-snapshot-entity])}
  [connection eid]
  (db.core/query-for-entity connection
                            [get-tournament-snapshot-by-eid-query eid]
                            tournament-snapshot-entity))

(def get-tournament-snapshot-by-tournament-eid-query
  (load-tournament-query "get-tournament-snapshot-by-tournament-eid.sql"))

(defn get-tournament-snapshot-by-tournament-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   tournament-snapshot-entity])}
  [connection tournament-eid]
  (db.core/query-for-entity connection
                            [get-tournament-snapshot-by-tournament-eid-query tournament-eid]
                            tournament-snapshot-entity))

(defn create-tournament
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] create-tournament-specification]
                   tournament-entity])}
  [connection specification]
  (let [game (db.game/get-game-by-eid connection (:game-eid specification))]
    (jdbc/with-transaction [tx connection]
      (db.core/insert! tx
                       :tournament
                       (-> specification
                           (dissoc :game-eid)
                           (assoc :game-id (:id game))))
      (let [tournament (get-tournament-by-eid connection (:eid specification))]
        (db.core/insert! tx
                         :tournament_snapshot
                         {:eid              (random-uuid)
                          :tournament-id    (:id tournament)
                          :tournament-state (jsonista/write-value-as-string {:type (:tournament-type specification)
                                                                             :rounds []})
                          :version          1
                          :created-by-sub   (:created-by-sub specification)
                          :created-at       (Instant/now)
                          :updated-at       (Instant/now)
                          })
        tournament))))

(def get-tournament-snapshot-by-tournament-eid-query
  (load-tournament-query "get-tournament-snapshot-by-tournament-eid.sql"))

(defn get-tournament-snapshot-by-tournament-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   tournament-snapshot-entity])}
  [connection tournament-eid]
  (db.core/query-for-entity connection
                            [get-tournament-snapshot-by-tournament-eid-query tournament-eid]
                            tournament-snapshot-entity))

(def update-tournament-snapshot-by-tournament-eid-query
  (load-tournament-query "update-tournament-snapshot-by-tournament-eid.sql"))

(defn update-tournament-snapshot-by-tournament-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid update-tournament-snapshot-specification]
                   tournament-snapshot-entity])}
  [connection tournament-eid new-snapshot]
  (db.core/execute-one! connection
                        [update-tournament-snapshot-by-tournament-eid-query
                         (:tournament-state new-snapshot)
                         (:update-at new-snapshot)
                         tournament-eid])
  (get-tournament-snapshot-by-tournament-eid connection tournament-eid))
