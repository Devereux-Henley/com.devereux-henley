(ns com.devereux-henley.rts-data-access.schema.season
  "Malli schemas for the season-domain Datalevin query/mutation return
  shapes. Mirrors the legacy `:season` entity shape the SQLite query
  layer surfaced, minus `:id` and `:deleted-at`. `:name` stays
  optional — the handler falls back to `\"Season N\"` when absent."
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]))

(def season-result-schema
  (schema.contract/to-schema
   [:map
    [:eid        :uuid]
    [:league-eid [:maybe :uuid]]
    [:ordinal    [:maybe :int]]
    [:name       {:optional true} [:maybe :string]]
    [:start-at   [:maybe inst?]]
    [:end-at     [:maybe inst?]]
    [:version    [:maybe :int]]
    [:created-at [:maybe inst?]]
    [:updated-at [:maybe inst?]]]))

(def create-spec-schema
  (schema.contract/to-schema
   [:map
    [:eid        :uuid]
    [:league-eid :uuid]
    [:ordinal    [:int {:min 1}]]
    [:name       {:optional true} [:maybe :string]]
    [:start-at   inst?]
    [:end-at     inst?]]))

(def max-ordinal-schema
  (schema.contract/to-schema
   [:map
    [:max-ordinal [:int {:min 0}]]]))

(def conn-schema
  "Opaque Datalevin connection — see [[schema.draft/conn-schema]]."
  :any)

(def tx-report-schema
  "Datalevin transact!'s return value, passed through as-is."
  :any)
