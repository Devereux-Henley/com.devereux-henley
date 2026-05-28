(ns com.devereux-henley.rts-data-access.schema.game
  "Malli schemas for the game-domain Datalevin query return shapes."
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]))

(def game-schema
  (schema.contract/to-schema
   [:map
    [:type [:= :game/game]]
    [:eid :uuid]
    [:name [:maybe :string]]
    [:description [:maybe :string]]]))

(def faction-schema
  (schema.contract/to-schema
   [:map
    [:type [:= :game/faction]]
    [:eid :uuid]
    [:name [:maybe :string]]
    [:key [:maybe :string]]
    [:description [:maybe :string]]
    [:game-eid [:maybe :uuid]]]))

(def unit-summary-schema
  (schema.contract/to-schema
   [:map
    [:type [:= :game/unit]]
    [:eid :uuid]
    [:key [:maybe :string]]
    [:name [:maybe :string]]
    [:family-name [:maybe :string]]
    [:description [:maybe :string]]
    [:mark [:maybe :string]]
    [:lore [:maybe :string]]
    [:is-unique :int]
    [:game-eid [:maybe :uuid]]
    [:faction-eid [:maybe :uuid]]
    [:unit-type-eid [:maybe :uuid]]
    [:unit-type-name [:maybe :string]]
    [:unit-category-eid [:maybe :uuid]]
    [:unit-category-name [:maybe :string]]
    [:cost [:maybe :int]]]))

(def unit-detail-schema
  (schema.contract/to-schema
   [:map
    [:type [:= :game/unit]]
    [:eid :uuid]
    [:key [:maybe :string]]
    [:name [:maybe :string]]
    [:family-name [:maybe :string]]
    [:description [:maybe :string]]
    [:mark [:maybe :string]]
    [:lore [:maybe :string]]
    [:is-unique :int]
    [:game-eid [:maybe :uuid]]
    [:faction-eid [:maybe :uuid]]
    [:unit-type-eid [:maybe :uuid]]
    [:unit-type-name [:maybe :string]]
    [:unit-category-eid [:maybe :uuid]]
    [:unit-category-name [:maybe :string]]
    [:cost [:maybe :int]]
    [:unit-statistics {:optional true} [:maybe :any]]]))

(def game-mode-schema
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:name [:maybe :string]]
    [:description [:maybe :string]]
    [:draft-value [:maybe :int]]
    [:player-count [:maybe :int]]
    [:reinforcement-value [:maybe :int]]
    [:reinforcements-enabled [:maybe :boolean]]
    [:game-eid [:maybe :uuid]]]))

(def social-link-schema
  (schema.contract/to-schema
   [:map
    [:type [:= :game/social]]
    [:eid :uuid]
    [:url [:maybe :string]]
    [:game-eid [:maybe :uuid]]
    [:social-media-platform-eid [:maybe :uuid]]
    [:platform-name [:maybe :string]]
    [:platform-description [:maybe :string]]
    [:platform-url [:maybe :string]]]))

(def mount-row-schema
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key [:maybe :string]]
    [:name [:maybe :string]]
    [:icon-key [:maybe :string]]
    [:cost [:maybe :int]]
    [:stats-override [:maybe :string]]
    [:granted-ability-keys [:maybe [:vector :string]]]]))

(def item-row-schema
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key [:maybe :string]]
    [:name [:maybe :string]]
    [:category [:maybe :string]]
    [:cost [:maybe :int]]
    [:icon-key [:maybe :string]]]))

(def spell-row-schema
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key [:maybe :string]]
    [:name [:maybe :string]]
    [:mana-cost [:maybe :int]]
    [:cost [:maybe :int]]]))

(def ability-row-schema
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key [:maybe :string]]
    [:name [:maybe :string]]
    [:description [:maybe :string]]
    [:cost [:maybe :int]]]))

(def unit-level-cost-schema
  (schema.contract/to-schema
   [:map
    [:level :int]
    [:fixed-cost [:maybe :int]]
    [:cost-multiplier [:maybe :double]]
    [:fatigue [:maybe :int]]
    [:melee-cp [:maybe :double]]
    [:missile-cp [:maybe :double]]]))

(def family-variant-schema
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:name [:maybe :string]]
    [:mark [:maybe :string]]
    [:lore [:maybe :string]]
    [:lore-name [:maybe :string]]
    [:cost [:maybe :int]]]))

(def conn-schema
  "Opaque Datalevin connection — see schema.draft/conn-schema."
  :any)
