(ns com.devereux-henley.rts-data-access.query.game
  (:require
   [clojure.string :as str]
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract]
   [next.jdbc :as jdbc])
  (:import
   [java.sql Connection]
   [java.time Instant]))

(def get-draft-by-eid-query (resource/load-query-resource "game" "get-draft-by-eid.sql"))

(def get-drafts-for-player-query (resource/load-query-resource "game" "get-drafts-for-player.sql"))

(def get-drafts-for-player-by-game-query (resource/load-query-resource "game" "get-drafts-for-player-by-game.sql"))

(def get-game-mode-by-eid-query (resource/load-query-resource "game" "get-game-mode-by-eid.sql"))

(def get-game-modes-for-game-query (resource/load-query-resource "game" "get-game-modes-for-game.sql"))

(def game-query (resource/load-query-resource "game" "get-games.sql"))

(def get-faction-by-eid-query (resource/load-query-resource "game" "get-faction-by-eid.sql"))

(def get-factions-for-game-query (resource/load-query-resource "game" "get-factions-for-game.sql"))

(def get-socials-for-game-query (resource/load-query-resource "game" "get-socials-for-game.sql"))

(def get-game-social-link-by-eid-query (resource/load-query-resource "game" "get-game-social-link-by-eid.sql"))

(def get-unit-type-by-eid-query (resource/load-query-resource "game" "get-unit-type-by-eid.sql"))

(def get-unit-types-for-game-query (resource/load-query-resource "game" "get-unit-types-for-game.sql"))

(def get-unit-category-by-eid-query (resource/load-query-resource "game" "get-unit-category-by-eid.sql"))

(def get-unit-categories-for-game-query (resource/load-query-resource "game" "get-unit-categories-for-game.sql"))

(def get-unit-by-eid-query (resource/load-query-resource "game" "get-unit-by-eid.sql"))

(def get-units-for-game-query (resource/load-query-resource "game" "get-units-for-game.sql"))

(def get-units-for-faction-query (resource/load-query-resource "game" "get-units-for-faction.sql"))

(def get-draft-state-by-draft-query (resource/load-query-resource "game" "get-draft-state-by-draft.sql"))

(defn get-game-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/game-entity])}
  [connection eid]
  (jdbc.contract/entity-by-eid connection :game eid schema/game-entity))

(defn get-games
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection]]
                   [:sequential schema/game-entity]])}
  [connection]
  (jdbc.contract/query-for-entities connection [game-query] schema/game-entity))

(defn get-faction-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/faction-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-faction-by-eid-query eid] schema/faction-entity))

(defn get-factions-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/faction-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-factions-for-game-query game-eid] schema/faction-entity))

(defn get-socials-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/game-social-link-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-socials-for-game-query game-eid] schema/game-social-link-entity))

(defn get-game-social-link-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/game-social-link-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-game-social-link-by-eid-query eid] schema/game-social-link-entity))

(defn get-unit-type-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/unit-type-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-unit-type-by-eid-query eid] schema/unit-type-entity))

(defn get-unit-types-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/unit-type-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-unit-types-for-game-query game-eid] schema/unit-type-entity))

(defn get-unit-category-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/unit-category-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-unit-category-by-eid-query eid] schema/unit-category-entity))

(defn get-unit-categories-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/unit-category-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-unit-categories-for-game-query game-eid] schema/unit-category-entity))

(defn get-unit-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/unit-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-unit-by-eid-query eid] schema/unit-entity))

(defn get-units-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/unit-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-units-for-game-query game-eid] schema/unit-entity))

(defn get-units-for-faction
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/unit-entity]])}
  [connection faction-eid]
  (jdbc.contract/query-for-entities connection [get-units-for-faction-query faction-eid] schema/unit-entity))

(defn get-draft-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/draft-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-draft-by-eid-query eid] schema/draft-entity))

(defn get-drafts-for-player
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :string]
                   [:sequential schema/draft-entity]])}
  [connection player-sub]
  (jdbc.contract/query-for-entities connection [get-drafts-for-player-query player-sub] schema/draft-entity))

(defn get-drafts-for-player-by-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :string :uuid]
                   [:sequential schema/draft-entity]])}
  [connection player-sub game-eid]
  (jdbc.contract/query-for-entities connection [get-drafts-for-player-by-game-query player-sub game-eid] schema/draft-entity))

(defn get-game-mode-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/game-mode-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-game-mode-by-eid-query eid] schema/game-mode-entity))

(defn get-game-modes-for-game
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential schema/game-mode-entity]])}
  [connection game-eid]
  (jdbc.contract/query-for-entities connection [get-game-modes-for-game-query game-eid] schema/game-mode-entity))

(defn get-spells-by-keys
  [connection spell-keys]
  (when (seq spell-keys)
    (let [placeholders (str/join "," (repeat (count spell-keys) "?"))
          sql          (str "SELECT key, name, mana_cost, cost FROM spell WHERE key IN (" placeholders ")")]
      (into {} (map (fn [row] [(:key row) row])
                    (jdbc.contract/query-for-entities connection (into [sql] spell-keys) schema/spell-entity))))))

(defn get-abilities-by-keys
  [connection ability-keys]
  (when (seq ability-keys)
    (let [placeholders (str/join "," (repeat (count ability-keys) "?"))
          sql          (str "SELECT eid, key, name, description FROM ability WHERE key IN (" placeholders ")")]
      (into {} (map (fn [row] [(:key row) row])
                    (jdbc.contract/query-for-entities connection (into [sql] ability-keys) schema/ability-entity))))))

(defn get-draft-state-by-draft
  [connection draft-eid]
  (jdbc.contract/query-for-entity connection [get-draft-state-by-draft-query draft-eid] schema/draft-state-entity))

(defn upsert-draft-state
  [connection draft-eid state-json-str]
  (let [draft (get-draft-by-eid connection draft-eid)]
    (jdbc.contract/execute-one!
     connection
     ["INSERT INTO draft_state (draft_id, state, updated_at)
       VALUES (?, ?, ?)
       ON CONFLICT(draft_id) DO UPDATE SET state = excluded.state, updated_at = excluded.updated_at"
      (:id draft) state-json-str (str (Instant/now))])))

(defn create-draft
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] schema/create-draft-params]
                   schema/draft-entity])}
  [connection specification]
  (let [game-mode (get-game-mode-by-eid connection (:game-mode-eid specification))
        faction   (get-faction-by-eid connection (:faction-eid specification))]
    (jdbc/with-transaction [tx connection]
      (jdbc.contract/insert! tx
                             :draft
                             (-> specification
                                 (dissoc :game-mode-eid :faction-eid)
                                 (assoc :game-mode-id (:id game-mode))
                                 (assoc :faction-id (:id faction))))
      (get-draft-by-eid connection (:eid specification)))))
