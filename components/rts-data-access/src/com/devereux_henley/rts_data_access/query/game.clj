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

(def get-family-variants-by-eid-query (resource/load-query-resource "game" "get-family-variants-by-eid.sql"))

(def get-draft-state-by-draft-query (resource/load-query-resource "game" "get-draft-state-by-draft.sql"))

(def get-items-for-unit-query (resource/load-query-resource "game" "get-items-for-unit.sql"))

(def get-mounts-for-unit-query (resource/load-query-resource "game" "get-mounts-for-unit.sql"))
(def get-spells-for-lore-query (resource/load-query-resource "game" "get-spells-for-lore.sql"))

(def get-mount-by-key-query (resource/load-query-resource "game" "get-mount-by-key.sql"))

(def get-unit-level-costs-query (resource/load-query-resource "game" "get-unit-level-costs.sql"))

(def upsert-draft-state-query (resource/load-query-resource "game" "upsert-draft-state.sql"))
(def update-draft-query (resource/load-query-resource "game" "update-draft.sql"))

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

(def family-variant-row-schema
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:mark [:maybe schema/mark-enum]]
    [:lore [:maybe :string]]
    [:lore-name {:optional true} [:maybe :string]]
    [:name {:optional true} :string]
    [:cost [:maybe :int]]]))

(defn get-family-variants-by-eid
  "Returns every unit row sharing the family (name + faction) of the
  given unit.  Single-variant families come back with one row; the
  draft-unit panel uses the count to decide whether to render the
  mark / lore selectors."
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:sequential family-variant-row-schema]])}
  [connection unit-eid]
  (jdbc.contract/query-for-entities
   connection
   [get-family-variants-by-eid-query unit-eid]
   family-variant-row-schema))

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
          sql          (str "SELECT eid, key, name, mana_cost, cost FROM spell WHERE key IN (" placeholders ")")]
      (into {} (map (fn [row] [(:key row) row])
                    (jdbc.contract/query-for-entities connection (into [sql] spell-keys) schema/spell-entity))))))

(defn get-abilities-by-keys
  [connection ability-keys]
  (when (seq ability-keys)
    (let [placeholders (str/join "," (repeat (count ability-keys) "?"))
          sql          (str "SELECT eid, key, name, description, cost FROM ability WHERE key IN (" placeholders ")")]
      (into {} (map (fn [row] [(:key row) row])
                    (jdbc.contract/query-for-entities connection (into [sql] ability-keys) schema/ability-entity))))))

(defn get-items-for-unit
  "Returns all active items linked to the given unit EID via the unit_item join table."
  [connection unit-eid]
  (jdbc.contract/query-for-entities connection [get-items-for-unit-query unit-eid] schema/item-entity))

(defn get-mounts-for-unit
  "Returns all active mounts linked to the given unit EID via the unit_mount
  join table. The per-unit mount cost is projected from unit_mount.cost into
  the mount map's :cost field."
  [connection unit-eid]
  (jdbc.contract/query-for-entities connection [get-mounts-for-unit-query unit-eid] schema/mount-entity))

(defn get-mount-by-key
  "Returns a single mount row by its ancillary `type` key, or nil."
  [connection mount-key]
  (jdbc.contract/query-for-entity connection [get-mount-by-key-query mount-key] schema/mount-entity))

(defn get-unit-level-costs
  "Returns the 10-row veteran-rank cost table as `level → entity-row`. The
  engine formula `round(base_cost * cost_multiplier) + fixed_cost` applies
  to every unit; level 0 is the no-op pass-through."
  [connection]
  (into (sorted-map)
        (map (juxt :level identity))
        (jdbc.contract/query-for-entities
         connection [get-unit-level-costs-query] schema/unit-level-cost-entity)))

(defn get-spells-for-lore
  "Returns the canonical spell list for a lore (by its stable key) via
  the spell_lore junction. One source of truth — every unit with access
  to this lore drafts from this same pool."
  [connection lore-key]
  (jdbc.contract/query-for-entities connection [get-spells-for-lore-query lore-key] schema/spell-entity))

(defn get-draft-state-by-draft
  [connection draft-eid]
  (jdbc.contract/query-for-entity connection [get-draft-state-by-draft-query draft-eid] schema/draft-state-entity))

(defn upsert-draft-state
  [connection draft-eid state-json-str]
  (let [draft (get-draft-by-eid connection draft-eid)]
    (jdbc.contract/execute-one!
     connection
     [upsert-draft-state-query (:id draft) state-json-str (str (Instant/now))])))

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

(defn update-draft
  "Applies a partial update to a draft. Currently the only mutable field
  is :name (nil clears the custom name and lets the default render).
  Returns the refreshed entity."
  [connection draft-eid {:keys [name]}]
  (jdbc.contract/execute-one!
   connection
   [update-draft-query name (str (Instant/now)) draft-eid])
  (get-draft-by-eid connection draft-eid))
