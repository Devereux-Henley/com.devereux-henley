(ns com.devereux-henley.rts-data-access.schema.league
  "Malli return-shape schemas for the league-domain Datalevin queries."
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
