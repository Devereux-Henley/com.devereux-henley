(ns com.devereux-henley.rts-domain.schema
  (:require
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.util]))

(def social-media-platform-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :social-media/by-eid} :uuid]
     [:name :string]
     [:description :string]
     [:platform-url :url]
     [:type [:= :social-media/platform]]])))

(def game-social-link-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :game/social-by-eid} :uuid]
     [:game-eid {:model/link :game/by-eid} :uuid]
     [:social-media-platform-eid {:model/link :social-media/by-eid} :uuid]
     [:type [:= :game/social]]
     [:url :url]
     [:_links
      [:map
       [:self :url]
       [:game :url]
       [:social-media-platform :url]]]
     [:_embedded {:optional true}
      [:map
       [:platform {:optional true} social-media-platform-resource]]]])))

(def unit-summary
  "Slim view of a game unit as it appears inside the faction-resource's
   :_embedded.units-by-category groups — enough to identify, name, and link
   to the full unit resource without dragging along unit-statistics JSON or
   audit columns."
  [:map
   [:eid :uuid]
   [:type {:optional true} [:= :game/unit]]
   [:name {:min 1} :string]
   [:description {:optional true} :string]
   [:cost {:optional true} [:maybe :int]]
   [:unit-type-name {:optional true} :string]
   [:unit-category-name {:optional true} :string]
   [:game-eid {:optional true} :uuid]
   [:is-unique {:optional true} :int]])

(def unit-category-group
  "One group in a faction-resource's :_embedded.units-by-category — the
   category label plus the units that belong to it."
  [:map
   [:category :string]
   [:units [:sequential unit-summary]]])

(def faction-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :game/faction-by-eid} :uuid]
     [:game-eid {:model/link :game/by-eid} :uuid]
     [:type [:= :game/faction]]
     [:name {:min 1} :string]
     [:description {:min 1} :string]
     [:_links
      [:map
       [:self :url]
       [:game :url]]]
     [:_embedded {:optional true}
      [:map
       [:units-by-category {:optional true} [:sequential unit-category-group]]]]])))

(def game-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :game/by-eid} :uuid]
     [:type [:= :game/game]]
     [:name :string]
     [:description :string]
     [:_embedded {:optional true}
      [:map
       [:socials {:optional true} [:sequential game-social-link-resource]]
       [:factions {:optional true} [:sequential faction-resource]]]]])))

(def game-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource game-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/game]]])))

;; ─── Request body schemas ───────────────────────────────────────────────────

(def update-status-specification
  (schema.contract/to-schema
   [:map
    [:status data-access.contract/tournament-status-enum]]))

(def update-registration-specification
  (schema.contract/to-schema
   [:map
    [:closed-early :boolean]]))

(def phase-round-specification
  (schema.contract/to-schema
   [:map
    [:round-index :int]
    [:format {:optional true} data-access.contract/match-format-enum]]))

(def phase-specification
  (schema.contract/to-schema
   [:map
    [:phase-type data-access.contract/phase-type-enum]
    [:rounds [:sequential phase-round-specification]]]))

(def phase-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/phase]]
    [:tournament-eid :uuid]]))

(def round-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/round]]
    [:tournament-eid :uuid]]))

(def configure-phases-specification
  (schema.contract/to-schema
   [:map
    [:phases [:sequential phase-specification]]
    [:qualifier-count {:optional true} [:maybe :int]]]))

;; ─── Resource schemas ───────────────────────────────────────────────────────

(def tournament-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :tournament/by-eid} :uuid]
     [:type [:= :tournament/tournament]]
     [:name {:min 1} :string]
     [:description {:min 1} :string]
     [:game-eid {:model/link :game/by-eid} :uuid]
     [:created-by-sub :string]
     [:_links
      [:map
       [:self :url]
       [:game :url]]]])))

(def create-tournament-specification
  (schema.contract/to-schema
   [:map
    [:game-eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]
    [:timezone :timezone-id]
    [:registration-opens-at :local-datetime]
    [:registration-closes-at :local-datetime]]))

(def tournament-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource tournament-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/tournament]]])))

(def match-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :match/by-eid} :uuid]
     [:type [:= :tournament/match]]
     [:tournament-eid {:model/link :tournament/by-eid} :uuid]
     [:phase-index :int]
     [:round-index :int]
     [:bracket-type data-access.contract/bracket-type-enum]
     [:player-one-sub :string]
     [:player-two-sub [:maybe :string]]
     [:winner-sub [:maybe :string]]
     [:status data-access.contract/match-status-enum]
     [:format data-access.contract/match-format-enum]
     [:_links
      [:map
       [:self :url]
       [:tournament :url]]]])))

(def create-match-specification
  (schema.contract/to-schema
   [:map
    [:phase-index :int]
    [:round-index :int]
    [:player-one-sub :string]
    [:player-two-sub {:optional true} [:maybe :string]]
    [:format {:optional true} data-access.contract/match-format-enum]]))

(def record-result-specification
  (schema.contract/to-schema
   [:map
    [:winner-sub :string]]))

(def tournament-entry-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :tournament-entry/by-eid} :uuid]
     [:type [:= :tournament/entry]]
     [:tournament-eid {:model/link :tournament/by-eid} :uuid]
     [:player-sub :string]
     [:created-at :instant]
     [:_links
      [:map
       [:self :url]
       [:tournament :url]]]])))

;; ─── Subresource response schemas ───────────────────────────────────────────

(def standing-entry
  (schema.contract/to-schema
   [:map
    [:player-sub :string]
    [:wins :int]
    [:losses :int]
    [:draws :int]
    [:points :int]]))

(def tournament-entry-summary
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:type [:= :tournament/entry]]
    [:tournament-eid :uuid]
    [:player-sub :string]
    [:created-at :instant]]))

(def tournament-entries-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/entries]]
    [:entries [:sequential tournament-entry-summary]]]))

(def tournament-status-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/status]]
    [:status data-access.contract/tournament-status-enum]
    [:available-transitions [:sequential :string]]]))

(def tournament-advance-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/advance-success]]
    [:state [:map {:closed false}
             [:status data-access.contract/tournament-status-enum]]]]))

(def tournament-registration-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/registration]]
    [:opens-at [:maybe :string]]
    [:closes-at [:maybe :string]]
    [:timezone [:maybe :string]]
    [:closed-early :boolean]]))

(def tournament-entry-deleted-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/entry-deleted]]
    [:message :string]]))

(def tournament-match-summary
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:type [:= :tournament/match]]
    [:tournament-eid :uuid]
    [:phase-index :int]
    [:round-index :int]
    [:bracket-type data-access.contract/bracket-type-enum]
    [:player-one-sub :string]
    [:player-two-sub [:maybe :string]]
    [:winner-sub [:maybe :string]]
    [:status data-access.contract/match-status-enum]
    [:format data-access.contract/match-format-enum]]))

(def tournament-matches-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/matches]]
    [:matches [:sequential tournament-match-summary]]]))

;; ─── Bracket-view shapes ────────────────────────────────────────────────────
;; Describe the projections built by rules/group-matches-by-round and
;; rules/group-matches-by-phase for the tournament-index template.

(def bracket-slot-status-enum
  "Status of a slot in a rendered bracket. Real matches carry the DB
   match status; unfilled slots in a pre-rendered skeleton carry \"tbd\"."
  [:enum "pending" "complete" "tbd"])

(def bracket-match-slot
  "Either a real match or a TBD placeholder. Open on extra keys because
   real matches arrive straight from the match-entity shape while
   placeholders carry only a skeletal form."
  (schema.contract/to-schema
   [:map {:closed false}
    [:player-one-sub [:maybe :string]]
    [:player-two-sub [:maybe :string]]
    [:winner-sub [:maybe :string]]
    [:status bracket-slot-status-enum]
    [:placeholder {:optional true} :boolean]]))

(def bracket-round
  "One round's worth of slots in a bracket or swiss-style phase."
  (schema.contract/to-schema
   [:map
    [:phase :int]
    [:round :int]
    [:phase-type [:maybe :string]]
    [:bracket-type {:optional true} :string]
    [:total-rounds :int]
    [:matches [:sequential bracket-match-slot]]]))

(def phase-group
  "Aggregated view of a tournament phase. Elimination phases expose
   :winners-bracket (and for double-elim :losers-bracket + :grand-final);
   swiss / round-robin phases expose a flat :rounds vector."
  (schema.contract/to-schema
   [:map
    [:phase :int]
    [:phase-type [:maybe :string]]
    [:rounds {:optional true} [:sequential bracket-round]]
    [:winners-bracket {:optional true} [:sequential bracket-round]]
    [:losers-bracket {:optional true} [:sequential bracket-round]]
    [:grand-final {:optional true} [:sequential bracket-round]]]))

(def tournament-match-result-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/match-result-recorded]]
    [:match-eid :uuid]
    [:standings [:sequential standing-entry]]]))

(def resource-identifier
  [:map
   [:eid :uuid]
   [:version :int]])

(def draft-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :draft/by-eid} :uuid]
     [:type [:= :game/draft]]
     [:game-mode-eid {:model/link :game-mode/by-eid} :uuid]
     [:faction-eid {:model/link :game/faction-by-eid} :uuid]
     [:player-sub :string]])))

(def create-draft-specification
  (schema.contract/to-schema
   [:map
    [:game-eid :uuid]
    [:game-mode-eid :uuid]
    [:faction-eid :uuid]]))

(def draft-error-response
  (schema.contract/to-schema
   [:map
    [:type [:enum :draft/add-error :draft/update-error]]
    [:message :string]]))

(def draft-unit-stat
  (schema.contract/to-schema
   [:map
    [:stat :string]
    [:icon :string]
    [:value :any]
    [:percentage :int]
    [:tooltip {:optional true} [:maybe :string]]
    [:damage-types {:optional true} [:sequential :string]]
    [:attack-modifiers {:optional true} [:sequential :string]]]))

(def draft-item
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key :string]
    [:name :string]
    [:category :string]
    [:cost :int]
    [:selected {:optional true} :boolean]
    [:icon-key {:optional true} [:maybe :string]]]))

(def draft-ability
  (schema.contract/to-schema
   [:map
    [:key :string]
    [:name :string]
    [:eid {:optional true} [:maybe :uuid]]
    [:description {:optional true} [:maybe :string]]
    [:selected {:optional true} :boolean]
    [:cost :int]]))

(def draft-spell
  (schema.contract/to-schema
   [:map
    [:key :string]
    [:eid {:optional true} [:maybe :uuid]]
    [:name :string]
    [:mana-cost :int]
    [:selected {:optional true} :boolean]
    [:cost :int]]))

(def draft-mount
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key :string]
    [:name :string]
    [:cost :int]
    [:selected {:optional true} :boolean]
    [:icon-key {:optional true} [:maybe :string]]]))

(def draft-unit-resource
  "A unit viewed in the context of a specific draft — the full game-unit
   fields (name, stats, abilities, attributes, health/barrier) merged with
   the per-draft option catalog (items, mounts, draftable spells) and a
   flag for whether reinforcements are enabled for the enclosing game mode.
   The resource's :eid is the unit's own eid; :draft-eid links to the
   parent draft."
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :draft-unit/by-eid} :uuid]
     [:type [:= :draft/unit]]
     [:draft-eid {:model/link :draft/by-eid} :uuid]
     [:game-eid {:model/link :game/by-eid} :uuid]
     [:name :string]
     [:description :string]
     [:unit-type-name :string]
     [:unit-category-name :string]
     [:cost [:maybe :int]]
     [:health {:optional true} [:maybe :int]]
     [:barrier {:optional true} [:maybe :int]]
     [:unit-statistics [:sequential draft-unit-stat]]
     [:attributes {:optional true}
      [:sequential [:map
                    [:key :string]
                    [:icon :string]
                    [:label :string]]]]
     [:parsed-abilities {:optional true} [:sequential draft-ability]]
     [:passive-abilities {:optional true} [:sequential draft-ability]]
     [:draftable-abilities {:optional true} [:sequential draft-ability]]
     [:items {:optional true} [:sequential draft-item]]
     [:mounts {:optional true} [:sequential draft-mount]]
     [:passive-spells {:optional true} [:sequential draft-spell]]
     [:draftable-spells {:optional true} [:sequential draft-spell]]
     [:has-passives {:optional true} :boolean]
     [:validation {:optional true}
      [:map
       [:can-add-to-reinforcements? :boolean]]]
     [:_links
      [:map
       [:self :url]
       [:draft :url]]]])))

(def draft-entry-resource
  "A placed draft entry — addressing (entry eid, draft-eid, unit-eid,
   section) plus the selection state the player has stored on this entry
   (:mount, :abilities, :spells, :items as key lists). Clients that want
   the game unit's catalog data request it via `?embed=unit`, which
   populates :_embedded.unit with a full draft-unit-resource whose
   draftable options carry :selected flags pre-marked from the entry's
   stored selections."
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :draft-entry/by-eid} :uuid]
     [:type [:= :draft/entry]]
     [:draft-eid {:model/link :draft/by-eid} :uuid]
     [:unit-eid {:model/link :draft-unit/by-eid} :uuid]
     [:section [:enum "main" "reinforcements"]]
     [:mount {:optional true} [:maybe :string]]
     [:abilities {:optional true} [:sequential :string]]
     [:spells {:optional true} [:sequential :string]]
     [:items {:optional true} [:sequential :string]]
     [:_links
      [:map
       [:self :url]
       [:draft :url]
       [:unit :url]]]
     [:_embedded {:optional true}
      [:map
       [:unit {:optional true} draft-unit-resource]]]])))

(def draft-section-unit
  "A unit as it appears inside a rendered draft section: just enough fields
   to draw the slot card and target the specific placed entry."
  [:map
   [:eid :uuid]
   [:entry-eid :uuid]
   [:name :string]
   [:total-cost [:maybe :int]]
   [:is-lord {:optional true} :boolean]])

(def draft-section-budget
  "The meter-only projection of a section context: enough to render the
   budget group fragment without carrying unit lists."
  [:map
   [:section :string]
   [:section-label :string]
   [:section-id :string]
   [:section-cost :int]
   [:section-max {:optional true} [:maybe :int]]
   [:section-percentage :int]
   [:section-near-limit :boolean]
   [:section-over-budget :boolean]])

(def draft-section-ref
  "The addressing fields a mutation response uses to point at a section:
   enough to build URLs and OOB selector targets in a slot fragment."
  [:map
   [:section :string]
   [:section-id :string]
   [:section-label :string]
   [:draft-eid :uuid]])

(def draft-section-context
  (schema.contract/to-schema
   [:map
    [:section :string]
    [:section-label :string]
    [:section-id :string]
    [:is-main :boolean]
    [:draft-eid :uuid]
    [:section-cost :int]
    [:section-max {:optional true} [:maybe :int]]
    [:section-percentage :int]
    [:section-near-limit :boolean]
    [:section-over-budget :boolean]
    [:lord-unit {:optional true} [:maybe draft-section-unit]]
    [:non-lord-units [:sequential draft-section-unit]]
    [:section-units [:sequential draft-section-unit]]]))

(defn- ^:private scalar-or-seq->vec
  "json-enc serialises a group of same-named form inputs as a scalar when
  only one is selected and as an array when 2+ are selected. Our schema
  expects `[:sequential :string]` — coerce the scalar case up into a
  singleton vector so Malli validation passes for both shapes."
  [v]
  (cond
    (nil? v)        nil
    (sequential? v) v
    :else           [v]))

(def add-unit-to-draft-specification
  (schema.contract/to-schema
   [:map
    [:mount     {:optional true} [:maybe :string]]
    [:abilities {:optional true :decode/json scalar-or-seq->vec} [:sequential :string]]
    [:spells    {:optional true :decode/json scalar-or-seq->vec} [:sequential :string]]
    [:items     {:optional true :decode/json scalar-or-seq->vec} [:sequential :string]]]))

(def draft-add-response
  (schema.contract/to-schema
   [:map
    [:type [:= :draft/add-success]]
    [:section draft-section-ref]
    [:new-unit draft-section-unit]
    [:budget draft-section-budget]]))

(def draft-remove-response
  (schema.contract/to-schema
   [:map
    [:type [:= :draft/remove-success]]
    [:removed-entry-eid :uuid]
    [:removed-is-lord :boolean]
    [:budget draft-section-budget]]))

(def draft-update-response
  (schema.contract/to-schema
   [:map
    [:type [:= :draft/update-success]]
    [:entry-eid :uuid]
    [:total-cost [:maybe :int]]
    [:budget draft-section-budget]]))
