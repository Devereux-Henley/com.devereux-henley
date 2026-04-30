(ns com.devereux-henley.rts-data-access.query.replay
  (:require
   [clojure.string :as string]
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract])
  (:import
   [java.sql Connection]))

(def get-replay-by-eid-query (resource/load-query-resource "replay" "get-replay-by-eid.sql"))
(def get-units-by-keys-template (resource/load-query-resource "replay" "get-units-by-keys.sql"))
(def get-subfactions-by-keys-template (resource/load-query-resource "replay" "get-subfactions-by-keys.sql"))

(def unit-resolution-row-schema
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key [:maybe :string]]
    [:name :string]
    [:cost [:maybe :int]]
    [:unit-category-name :string]
    [:unit-type-name :string]
    [:faction-eid :uuid]
    [:faction-name :string]]))

(def subfaction-resolution-row-schema
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key :string]
    [:name :string]
    [:faction-eid :uuid]
    [:faction-name :string]
    [:faction-key [:maybe :string]]]))

(defn get-replay-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   schema/replay-entity])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-replay-by-eid-query (str eid)] schema/replay-entity))

(defn create-replay
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] schema/create-replay-params]
                   schema/replay-entity])}
  [connection specification]
  (jdbc.contract/insert! connection :replay specification)
  (get-replay-by-eid connection (:eid specification)))

(defn get-units-by-keys
  "Resolves a collection of engine `land_units` keys to unit rows.  Returns
  a vector of rows whose `key` is in the input set; missing keys are simply
  omitted (callers should fall back gracefully)."
  [connection unit-keys]
  (let [keys-vec (vec (distinct (filter some? unit-keys)))]
    (if (empty? keys-vec)
      []
      (let [placeholders (string/join "," (repeat (count keys-vec) "?"))
            sql          (format get-units-by-keys-template placeholders)]
        (jdbc.contract/query-for-entities
         connection
         (into [sql] keys-vec)
         unit-resolution-row-schema)))))

(defn get-subfactions-by-keys
  "Resolves a collection of engine `factions_tables` keys (the parser's
  `faction_key` field) to subfaction rows joined with their parent race
  (faction).  Returns a vector of rows whose `key` is in the input set;
  missing keys are simply omitted so callers can fall back to the raw key."
  [connection subfaction-keys]
  (let [keys-vec (vec (distinct (filter some? subfaction-keys)))]
    (if (empty? keys-vec)
      []
      (let [placeholders (string/join "," (repeat (count keys-vec) "?"))
            sql          (format get-subfactions-by-keys-template placeholders)]
        (jdbc.contract/query-for-entities
         connection
         (into [sql] keys-vec)
         subfaction-resolution-row-schema)))))
