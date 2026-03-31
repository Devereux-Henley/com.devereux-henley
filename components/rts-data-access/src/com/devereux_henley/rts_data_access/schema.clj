(ns com.devereux-henley.rts-data-access.schema
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]))

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

(def create-tournament-params
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:game-eid :uuid]
    [:title :string]
    [:description :string]
    [:tournament-start-datetime :instant]
    [:tournament-checkin-datetime :instant]
    [:tournament-type [:enum "elimination" "round-robin"]]
    [:competitor-type [:enum "player"]]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]]))

(def tournament-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:game-eid :uuid]
    [:title :string]
    [:description :string]
    [:tournament-start-datetime :instant]
    [:tournament-checkin-datetime :instant]
    [:tournament-type [:enum "elimination" "round-robin"]]
    [:competitor-type [:enum "player"]]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def tournament-snapshot-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:tournament-eid :uuid]
    [:tournament-state :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def update-tournament-snapshot-specification
  (schema.contract/to-schema
   [:map
    [:tournament-state :string]
    [:updated-at :instant]]))
