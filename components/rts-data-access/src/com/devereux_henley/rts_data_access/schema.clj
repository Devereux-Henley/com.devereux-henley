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
    [:name {:optional true} [:maybe :string]]
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
    [:name {:optional true} [:maybe :string]]
    [:game-mode-eid :uuid]
    [:faction-eid :uuid]
    [:faction-name {:optional true} :string]
    [:created-at-display {:optional true} :string]
    [:updated-at-display {:optional true} :string]
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
    [:league-eid {:optional true} [:maybe :uuid]]
    [:season-eid {:optional true} [:maybe :uuid]]
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
    [:league-eid {:optional true} [:maybe :uuid]]
    [:season-eid {:optional true} [:maybe :uuid]]
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
    [:created-at :instant]]))

;; ─── Tournament / match enums ───────────────────────────────────────────────
;; These mirror CHECK-constrained values (or the closed sets the domain layer
;; enforces) so Malli coercion rejects unexpected strings at the DB boundary.

(def tournament-status-enum [:enum "registration" "active" "complete" "cancelled"])

(def phase-type-enum [:enum "swiss" "round-robin" "single-elimination" "double-elimination"])

(def match-status-enum [:enum "pending" "complete"])

(def match-format-enum [:enum 1 3 5])

(def bracket-type-enum [:enum "winners" "losers" "grand-final"])

(def match-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:tournament-eid :uuid]
    [:phase-index :int]
    [:round-index :int]
    [:bracket-type bracket-type-enum]
    [:player-one-sub :string]
    [:player-two-sub [:maybe :string]]
    [:winner-sub [:maybe :string]]
    [:status match-status-enum]
    [:format match-format-enum]
    [:created-at :instant]
    [:updated-at :instant]]))

(def match-game-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:match-eid :uuid]
    [:game-index :int]
    [:winner-sub [:maybe :string]]
    [:replay-eid {:optional true} [:maybe :uuid]]
    [:uploader-local-alliance-index {:optional true} [:maybe :int]]
    [:player-one-draft-eid {:optional true} [:maybe :uuid]]
    [:player-two-draft-eid {:optional true} [:maybe :uuid]]
    [:created-at :instant]]))

(def replay-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:match-id-external :string]
    [:played-at :string]
    [:victory-condition [:maybe :string]]
    [:parser-format :string]
    [:parsed-json :string]
    [:uploader-local-alliance-index [:maybe :int]]
    [:uploaded-by-sub :string]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def create-replay-params
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:match-id-external :string]
    [:played-at :string]
    [:victory-condition {:optional true} [:maybe :string]]
    [:parser-format :string]
    [:parsed-json :string]
    [:uploader-local-alliance-index {:optional true} [:maybe :int]]
    [:uploaded-by-sub :string]
    [:created-at :instant]
    [:updated-at :instant]]))

;; ─── League / Season entities ────────────────────────────────────────────────

(def league-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:game-eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:created-by-sub :string]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def create-league-params
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

(def season-entity
  (schema.contract/to-schema
   [:map
    [:id :int]
    [:eid :uuid]
    [:league-eid :uuid]
    [:ordinal :int]
    [:name [:maybe :string]]
    [:start-at :instant]
    [:end-at :instant]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]
    [:deleted-at [:maybe :instant]]]))

(def create-season-params
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:league-eid :uuid]
    [:ordinal :int]
    [:name {:optional true} [:maybe :string]]
    [:start-at :instant]
    [:end-at :instant]
    [:version :int]
    [:created-at :instant]
    [:updated-at :instant]]))

(def faction-standings-row-entity
  (schema.contract/to-schema
   [:map
    [:faction-eid :uuid]
    [:faction-name :string]
    [:matches-played :int]
    [:wins :int]
    [:losses :int]]))

(def max-ordinal-entity
  (schema.contract/to-schema
   [:map
    [:max-ordinal :int]]))

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
