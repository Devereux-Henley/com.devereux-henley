(ns com.devereux-henley.rts-data-access.schema.league
  "Malli schemas for the league-domain Datalevin query/mutation return
  shapes. Mirrors the legacy `:league` entity shape the SQLite query
  layer surfaced, minus `:id` and `:deleted-at` which Datalevin has no
  use for."
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]))

(def league-result-schema
  (schema.contract/to-schema
   [:map
    [:eid            :uuid]
    [:game-eid       [:maybe :uuid]]
    [:name           [:maybe :string]]
    [:description    [:maybe :string]]
    [:created-by-sub [:maybe :string]]
    [:version        [:maybe :int]]
    [:created-at     [:maybe inst?]]
    [:updated-at     [:maybe inst?]]]))

(def create-spec-schema
  (schema.contract/to-schema
   [:map
    [:eid            :uuid]
    [:game-eid       :uuid]
    [:name           :string]
    [:description    :string]
    [:created-by-sub :string]]))

(def conn-schema
  "Opaque Datalevin connection — see [[schema.draft/conn-schema]]."
  :any)

(def tx-report-schema
  "Datalevin transact!'s return value, passed through as-is."
  :any)
