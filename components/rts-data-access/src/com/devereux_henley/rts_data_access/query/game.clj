(ns com.devereux-henley.rts-data-access.query.game
  "SQLite game-domain queries — only `get-draft-lock-info` remains."
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.resource :as resource]
   [com.devereux-henley.schema.contract :as schema.contract])
  (:import
   [java.sql Connection]))

(def get-draft-lock-info-query (resource/load-query-resource "game" "get-draft-lock-info.sql"))

(def draft-lock-info-schema
  "Shape of the row returned by `get-draft-lock-info` — the first
  tournament match that references the draft. `nil` means the draft
  isn't referenced anywhere yet (still editable)."
  (schema.contract/to-schema
   [:map
    [:match-eid       :uuid]
    [:tournament-eid  :uuid]
    [:tournament-name :string]]))

(defn get-draft-lock-info
  "Returns the first tournament match that references the given draft
  (by eid) or `nil` if no match does. The presence of any match row is
  what makes a draft read-only — locking is one-way and derived at
  request time rather than stored on `draft` directly. Returned shape:
  `{:match-eid :tournament-eid :tournament-name}`."
  {:malli/schema (schema.contract/to-schema
                  [:=>
                   [:cat [:instance Connection] :uuid]
                   [:maybe draft-lock-info-schema]])}
  [connection eid]
  (jdbc.contract/query-for-entity connection [get-draft-lock-info-query eid] draft-lock-info-schema))
