(ns com.devereux-henley.rts-data-access.schema.draft
  "Malli schemas for the draft domain — input specs, return shapes,
  and the opaque passthroughs the query/mutation surface declares."
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]))

(def draft-entry-schema
  (schema.contract/to-schema
   [:map
    [:entry-eid   :uuid]
    [:unit-eid    :uuid]
    [:section     [:enum :main :reinforcements]]
    [:ordinal     [:int {:min 0}]]
    [:level       [:int {:min 0}]]
    [:mount       {:optional true} :string]
    [:lore        {:optional true} :string]
    [:abilities   {:optional true} [:sequential :string]]
    [:spells      {:optional true} [:sequential :string]]
    [:items       {:optional true} [:sequential :string]]
    [:total-cost  {:optional true} :int]
    [:engine-cost {:optional true} :int]]))

(def draft-state-schema
  (schema.contract/to-schema
   [:map
    [:main           [:sequential draft-entry-schema]]
    [:reinforcements [:sequential draft-entry-schema]]]))

(def draft-result-schema
  (schema.contract/to-schema
   [:map
    [:eid            :uuid]
    [:name           [:maybe :string]]
    [:player-sub     :string]
    [:version        :int]
    [:game-mode-eid  [:maybe :uuid]]
    [:faction-eid    [:maybe :uuid]]
    [:faction-name   [:maybe :string]]
    [:game-eid       [:maybe :uuid]]
    [:created-at     [:maybe inst?]]
    [:updated-at     [:maybe inst?]]
    [:created-by-sub {:optional true} :string]]))

(def create-spec-schema
  (schema.contract/to-schema
   [:map
    [:eid            :uuid]
    [:player-sub     :string]
    [:game-mode-eid  :uuid]
    [:faction-eid    :uuid]
    [:name           {:optional true} [:maybe :string]]
    [:created-by-sub {:optional true} [:maybe :string]]]))

(def entry-add-spec-schema
  (schema.contract/to-schema
   [:map
    [:entry-eid   :uuid]
    [:unit-eid    :uuid]
    [:section     [:enum :main :reinforcements]]
    [:level       {:optional true} :int]
    [:mount       {:optional true} [:maybe :string]]
    [:lore        {:optional true} [:maybe :string]]
    [:abilities   {:optional true} [:sequential :string]]
    [:spells      {:optional true} [:sequential :string]]
    [:items       {:optional true} [:sequential :string]]
    [:total-cost  {:optional true} [:maybe :int]]
    [:engine-cost {:optional true} [:maybe :int]]]))

(def entry-update-attrs-schema
  (schema.contract/to-schema
   [:map
    [:unit-eid    {:optional true} :uuid]
    [:section     {:optional true} [:enum :main :reinforcements]]
    [:level       {:optional true} :int]
    [:mount       {:optional true} [:maybe :string]]
    [:lore        {:optional true} [:maybe :string]]
    [:abilities   {:optional true} [:sequential :string]]
    [:spells      {:optional true} [:sequential :string]]
    [:items       {:optional true} [:sequential :string]]
    [:total-cost  {:optional true} [:maybe :int]]
    [:engine-cost {:optional true} [:maybe :int]]]))

(def conn-schema
  "Opaque Datalevin connection — pre-validating it would couple this ns
  to datalevin internals for no gain."
  :any)

(def tx-report-schema
  "Datalevin transact!'s return value, passed through as-is."
  :any)
