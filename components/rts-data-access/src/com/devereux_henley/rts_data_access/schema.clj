(ns com.devereux-henley.rts-data-access.schema
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core :as m]
   [malli.transform :as mt]))

(def game-mode-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:draft-value :int]
    [:player-count :int]
    [:reinforcement-value :int]
    [:reinforcements-enabled :int]
    [:game-eid :uuid]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def create-draft-params
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:game-mode-eid :uuid]
    [:faction-eid :uuid]
    [:player-sub :string]
    [:version :int]
    [:created-by-sub :string]
    [:created-at :instant]
    [:updated-at :instant]]))

(def draft-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:game-mode-eid :uuid]
    [:faction-eid :uuid]
    [:player-sub :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def game-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def faction-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:game-eid :uuid]
    [:description {:min 1} :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def game-social-link-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:url :url]
    [:game-eid :uuid]
    [:social-media-platform-eid :uuid]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def unit-type-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:game-eid :uuid]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def unit-category-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:game-eid :uuid]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def unit-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:game-eid :uuid]
    [:unit-type-eid :uuid]
    [:unit-type-name :string]
    [:unit-category-eid :uuid]
    [:unit-category-name :string]
    [:cost [:maybe :int]]
    [:unit-statistics :string]
    [:is-unique :int]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def platform-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:platform-url {:min 1} :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def ability-entity
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key :string]
    [:name :string]
    [:description [:maybe :string]]
    [:cost :int]]))

(def spell-entity
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key :string]
    [:name :string]
    [:mana-cost :int]
    [:cost :int]]))

(def item-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:key :string]
    [:name :string]
    [:category :string]
    [:cost :int]
    [:icon-key [:maybe :string]]]))

(def mount-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:key :string]
    [:name :string]
    [:icon-key [:maybe :string]]
    [:cost :int]]))

(def draft-state-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:state :string]
    [:updated-at :instant]]))

(def tournament-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:game-eid :uuid]
    [:created-by-sub :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def create-tournament-params
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:game-eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:created-by-sub :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]]))

(def tournament-state-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:state :string]
    [:updated-at :instant]]))

(def tournament-entry-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:tournament-eid :uuid]
    [:player-sub :string]
    [:created-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def match-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:tournament-eid :uuid]
    [:phase-index :int]
    [:round-index :int]
    [:player-one-sub :string]
    [:player-two-sub [:maybe :string]]
    [:winner-sub [:maybe :string]]
    [:status :string]
    [:created-at :instant]
    [:updated-at :instant]]))

;; Schema for the known structured fields in the raw unit-statistics JSON (string keys).
;; :closed false allows the extra dynamic stat keys to pass through.
(def unit-statistics-raw-schema
  (m/schema
   [:map {:closed false}
    ["abilities"           {:optional true, :default []} [:sequential :string]]
    ["draftable-spells"    {:optional true, :default []} [:sequential [:map ["key" :string]]]]
    ["draftable-abilities" {:optional true, :default []} [:sequential :string]]
    ["mounts"              {:optional true, :default []}
     [:sequential [:map ["name" [:maybe :string]] ["cost" [:maybe :int]]]]]
    ["equipment"           {:optional true, :default []} [:sequential :any]]]))

(def unit-statistics-transformer
  (mt/default-value-transformer {::mt/add-optional-keys true}))

