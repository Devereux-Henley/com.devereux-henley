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

(def mark-enum
  "Closed set of valid Mark of Chaos values for the unit `mark` column.
  Matches the CHECK constraint in `000006-create-unit-table.up.sql` and
  the runtime set in `rts-domain.domain.mark/marks` — keep all three in
  sync.  Re-exported via `rts-data-access.contract/mark-enum` for
  rts-domain (and any other consumer outside this component)."
  [:enum "khorne" "nurgle" "slaanesh" "tzeentch" "undivided"])

(def unit-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:name {:min 1} :string]
    [:family-name {:optional true} [:maybe :string]]
    [:description {:min 1} :string]
    [:game-eid :uuid]
    [:unit-type-eid :uuid]
    [:unit-type-name :string]
    [:unit-category-eid :uuid]
    [:unit-category-name :string]
    [:cost [:maybe :int]]
    [:unit-statistics :string]
    [:mark [:maybe mark-enum]]
    [:lore {:optional true} [:maybe :string]]
    [:family-variant-count {:optional true} :int]
    [:is-unique :int]
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
    [:cost :int]
    [:stats-override {:optional true} [:maybe :string]]
    [:granted-ability-keys {:optional true} [:maybe :string]]]))

(def lore-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:key :string]
    [:name :string]
    [:cost :int]
    [:portrait-key {:optional true} [:maybe :string]]]))

(def unit-level-cost-entity
  (schema.contract/to-schema
   [:map
    [:level :int]
    [:fixed-cost :int]
    [:cost-multiplier :double]
    [:fatigue :int]
    [:melee-cp :double]
    [:missile-cp :double]]))

;; ─── Tournament / match enums ───────────────────────────────────────────────
;; Closed value sets for tournament/match fields, shared with the domain
;; resource schemas.

(def tournament-status-enum [:enum "registration" "active" "complete" "cancelled"])

(def phase-type-enum [:enum "swiss" "round-robin" "single-elimination" "double-elimination"])

(def match-status-enum [:enum "pending" "complete"])

(def match-format-enum [:enum 1 3 5])

(def bracket-type-enum [:enum "winners" "losers" "grand-final"])

;; ─── Stats entities (SQLite) ─────────────────────────────────────────────────

(def faction-standings-row-entity
  (schema.contract/to-schema
   [:map
    [:faction-eid :uuid]
    [:faction-name :string]
    [:matches-played :int]
    [:wins :int]
    [:losses :int]]))

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
