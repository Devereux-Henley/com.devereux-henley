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
     [:eid {:model/link :social-link/by-eid} :uuid]
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
  "Slim view of a game unit. Used inside faction-resource's
   :_embedded.units-by-category groups and as the row schema for
   /api/unit collections. :model/link on :eid lets the transformer
   resolve :_links.self to /api/unit/:eid so any nesting walks back
   to the canonical unit resource."
  [:map {:model/type :model/model}
   [:eid {:model/link :unit/by-eid} :uuid]
   [:type {:optional true} [:= :game/unit]]
   [:name {:min 1} :string]
   [:family-name {:optional true} [:maybe :string]]
   [:description {:optional true} :string]
   [:cost {:optional true} [:maybe :int]]
   [:unit-type-name {:optional true} :string]
   [:unit-category-name {:optional true} :string]
   [:mark {:optional true} [:maybe data-access.contract/mark-enum]]
   [:family-variant-count {:optional true} :int]
   [:game-eid {:optional true :model/link :game/by-eid} :uuid]
   [:is-unique {:optional true} :int]
   [:_links {:optional true}
    [:map [:self :url] [:game {:optional true} :url]]]])

(def unit-resource
  "Full unit resource served at /api/unit/:eid. Carries the parsed
   unit-statistics (stats, health, barrier, attributes) plus embedded
   collections of related abilities, spells, items, and mounts."
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :unit/by-eid} :uuid]
     [:type [:= :game/unit]]
     [:name :string]
     [:family-name {:optional true} [:maybe :string]]
     [:description {:optional true} :string]
     [:cost {:optional true} [:maybe :int]]
     [:unit-type-name {:optional true} :string]
     [:unit-category-name {:optional true} :string]
     [:mark {:optional true} [:maybe data-access.contract/mark-enum]]
     [:family-variant-count {:optional true} :int]
     [:game-eid {:optional true :model/link :game/by-eid} :uuid]
     [:is-unique {:optional true} :int]
     [:health {:optional true} [:maybe :int]]
     [:barrier {:optional true} [:maybe :int]]
     [:stats {:optional true} [:sequential [:map {:closed false}]]]
     [:attributes {:optional true} [:sequential [:map {:closed false}]]]
     [:_links [:map [:self :url] [:game {:optional true} :url]]]
     [:_embedded {:optional true}
      [:map
       [:abilities {:optional true} [:sequential [:map {:closed false}]]]
       [:spells {:optional true} [:sequential [:map {:closed false}]]]
       [:items {:optional true} [:sequential [:map {:closed false}]]]
       [:mounts {:optional true} [:sequential [:map {:closed false}]]]]]])))

(def unit-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource unit-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/unit]]])))

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
     [:eid {:model/link :faction/by-eid} :uuid]
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

(def faction-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource faction-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/faction]]])))

(def game-social-link-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource game-social-link-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/social-link]]])))

(def game-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource game-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/game]]])))

;; ─── Request body schemas ───────────────────────────────────────────────────

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

(def round-response
  (schema.contract/to-schema
   [:map {:model/type :model/model}
    [:type [:= :tournament/round]]
    [:tournament-eid {:model/link :tournament/by-eid} :uuid]
    [:_links [:map [:tournament :url]]]]))

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
     {:model/sub-resources {:status       :tournament/status
                            :registration :tournament/registration
                            :entries      :collection/tournament-entry
                            :matches      :collection/match
                            :round        :tournament/round}}
     [:eid {:model/link :tournament/by-eid} :uuid]
     [:type [:= :tournament/tournament]]
     [:name {:min 1} :string]
     [:description {:min 1} :string]
     [:game-eid {:model/link :game/by-eid} :uuid]
     [:league-eid {:optional true :model/link :league/by-eid} :uuid]
     [:season-eid {:optional true :model/link :season/by-eid} :uuid]
     [:created-by-sub :string]
     [:_links
      [:map
       [:self :url]
       [:game :url]
       [:league {:optional true} :url]
       [:season {:optional true} :url]
       [:status :url]
       [:registration :url]
       [:entries :url]
       [:matches :url]
       [:round :url]]]])))

(def create-tournament-specification
  (schema.contract/to-schema
   [:map
    [:game-eid :uuid]
    [:league-eid {:optional true} [:maybe :uuid]]
    [:season-eid {:optional true} [:maybe :uuid]]
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
     {:model/sub-resources {:games :collection/match-game}}
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
       [:tournament :url]
       [:games :url]]]])))

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
   [:map {:model/type :model/model}
    [:type [:= :tournament/entries]]
    [:tournament-eid {:model/link :tournament/by-eid} :uuid]
    [:entries [:sequential tournament-entry-summary]]
    [:_links [:map [:tournament :url]]]]))

(def tournament-status-response
  (schema.contract/to-schema
   [:map {:model/type :model/model}
    [:type [:= :tournament/status]]
    [:tournament-eid {:model/link :tournament/by-eid} :uuid]
    [:status data-access.contract/tournament-status-enum]
    [:available-transitions [:sequential :string]]
    [:_links [:map [:tournament :url]]]]))

(def tournament-started-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/started]]
    [:state [:map {:closed false}
             [:status data-access.contract/tournament-status-enum]]]]))

(def tournament-completed-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/completed]]
    [:state [:map {:closed false}
             [:status data-access.contract/tournament-status-enum]]]]))

(def tournament-cancelled-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/cancelled]]
    [:state [:map {:closed false}
             [:status data-access.contract/tournament-status-enum]]]]))

(def tournament-registration-closed-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/registration-closed]]
    [:state [:map {:closed false}
             [:status data-access.contract/tournament-status-enum]]]]))

(def tournament-registration-response
  (schema.contract/to-schema
   [:map {:model/type :model/model}
    [:type [:= :tournament/registration]]
    [:tournament-eid {:model/link :tournament/by-eid} :uuid]
    [:opens-at [:maybe :string]]
    [:closes-at [:maybe :string]]
    [:timezone [:maybe :string]]
    [:closed-early :boolean]
    [:_links [:map [:tournament :url]]]]))

(def tournament-entry-deleted-response
  (schema.contract/to-schema
   [:map
    [:type [:= :tournament/entry-deleted]]
    [:message :string]]))

(def tournament-match-summary
  (schema.contract/to-schema
   [:map {:model/type :model/model}
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
    [:_links [:map [:self :url] [:tournament :url]]]]))

(def tournament-matches-response
  (schema.contract/to-schema
   [:map {:model/type :model/model}
    [:type [:= :collection/match]]
    [:tournament-eid {:optional true :model/link :tournament/by-eid} :uuid]
    [:matches [:sequential tournament-match-summary]]
    [:_links {:optional true} [:map [:tournament {:optional true} :url]]]]))

(def tournament-games-response
  (schema.contract/to-schema
   [:map {:model/type :model/model}
    [:type [:= :collection/match-game]]
    [:tournament-eid {:optional true :model/link :tournament/by-eid} :uuid]
    [:match-eid {:model/link :match/by-eid} :uuid]
    [:games [:sequential [:map {:closed false}]]]
    [:_links [:map [:tournament {:optional true} :url] [:match :url]]]]))

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

(def phase-response
  "Response for GET /api/tournament/:eid/phase/:phase-index — the phase
   details panel. Carries just what the Selmer template needs: the
   focused phase group plus the tournament-wide standings that back
   the swiss / round-robin view. `:game-eid` rides along so the match
   cards can build `View lineup` URLs to each player's draft (since
   the draft view path is game-scoped). `:tournament-eid` drives the
   parent link via `_links.tournament`."
  (schema.contract/to-schema
   [:map {:model/type :model/model}
    [:type [:= :tournament/phase]]
    [:tournament-eid {:model/link :tournament/by-eid} :uuid]
    [:phase-group phase-group]
    [:tournament-state [:map {:closed false}
                        [:standings [:sequential standing-entry]]]]
    [:game-eid {:model/link :game/by-eid} :uuid]
    [:data [:map [:eid :uuid]]]
    [:_links [:map [:tournament :url] [:game :url]]]]))

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
     [:game-mode-eid :uuid]
     [:faction-eid {:model/link :faction/by-eid} :uuid]
     [:name {:optional true} [:maybe :string]]
     [:display-name {:optional true} :string]
     [:player-sub :string]
     [:_links
      [:map
       [:self :url]
       [:faction :url]]]])))

(def create-draft-specification
  (schema.contract/to-schema
   [:map
    [:game-eid :uuid]
    [:game-mode-eid :uuid]
    [:faction-eid :uuid]
    [:name {:optional true} [:maybe [:string {:max 60}]]]]))

(def update-draft-specification
  "Partial update on a draft. Currently only the custom :name is mutable
  — empty string clears the stored name so the default (faction + date)
  renders again."
  (schema.contract/to-schema
   [:map
    [:name {:optional true} [:maybe [:string {:max 60}]]]]))

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
    [:icon-key {:optional true} [:maybe :string]]
    [:stats-override {:optional true} [:maybe [:sequential draft-unit-stat]]]
    [:health-override {:optional true} [:maybe :int]]
    [:barrier-override {:optional true} [:maybe :int]]
    [:attributes-override {:optional true}
     [:maybe [:sequential [:map
                           [:key :string]
                           [:icon :string]
                           [:label :string]]]]]
    [:granted-abilities {:optional true} [:sequential draft-ability]]]))

(def family-mark-option
  "One row in the Mark of Chaos selector — just the eid the change
  swaps to, the mark label, and the variant's cost.  No `:name` /
  `:lore` because the selector doesn't render them; full per-variant
  data lives on the unit row that the eid resolves to."
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:mark [:maybe data-access.contract/mark-enum]]
    [:cost [:maybe :int]]]))

(def family-lore-option
  "One row in the Lore of Magic selector — the eid the change swaps
  to, the variant's cost, and a `:lore-label` (the canonical suffix
  like \"Death\" / \"High\") derived from the variant's `:lore`
  catalogue display name (with the variant-name parenthetical as a
  fallback)."
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:cost [:maybe :int]]
    [:lore-label {:optional true} [:maybe :string]]]))

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
     [:mark {:optional true} [:maybe data-access.contract/mark-enum]]
     [:lore {:optional true} [:maybe :string]]
     [:family-variant-count {:optional true} :int]
     [:family-name {:optional true} [:maybe :string]]
     [:family-marks {:optional true} [:sequential family-mark-option]]
     [:family-lores {:optional true} [:sequential family-lore-option]]
     [:cost [:maybe :int]]
     [:total-cost {:optional true} [:maybe :int]]
     [:level {:optional true} [:int {:min 0 :max 9}]]
     [:mount {:optional true} [:maybe :string]]
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
     [:mount-granted-abilities {:optional true} [:sequential draft-ability]]
     [:items {:optional true} [:sequential draft-item]]
     [:mounts {:optional true} [:sequential draft-mount]]
     [:passive-spells {:optional true} [:sequential draft-spell]]
     [:draftable-spells {:optional true} [:sequential draft-spell]]
     [:has-passives {:optional true} :boolean]
     [:locked? {:optional true} :boolean]
     [:validation {:optional true}
      [:map
       [:can-add-to-reinforcements? :boolean]]]
     [:_links
      [:map
       [:self :url]
       [:draft :url]
       [:game :url]]]])))

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
     [:level {:optional true} [:int {:min 0 :max 9}]]
     [:abilities {:optional true} [:sequential :string]]
     [:spells {:optional true} [:sequential :string]]
     [:items {:optional true} [:sequential :string]]
     [:locked? {:optional true} :boolean]
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
   to draw the slot card and target the specific placed entry.  The
   slot's portrait is derived directly from `:eid` (each variant unit
   row has its own portrait)."
  [:map
   [:eid :uuid]
   [:entry-eid :uuid]
   [:name :string]
   [:total-cost [:maybe :int]]
   [:level {:optional true} [:int {:min 0 :max 9}]]
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
    ;; Slot-edit family switching: when present, the entry's `:unit-eid`
    ;; gets swapped to the supplied variant (same family — different
    ;; mark and/or different lore) and the existing mount / items /
    ;; abilities / spells selections are cleared because they're keyed
    ;; to the old row's catalog.
    [:unit-eid  {:optional true} :uuid]
    [:mount     {:optional true} [:maybe :string]]
    [:level     {:optional true} [:int {:min 0 :max 9}]]
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
    [:slot-portrait-key :string]
    [:budget draft-section-budget]
    [:entry {:optional true} [:maybe draft-entry-resource]]]))

;; ─── League / Season / Stats resources ──────────────────────────────────────

(def league-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     {:model/sub-resources {:seasons           :collection/season
                            :faction-standings :collection/faction-standings}}
     [:eid {:model/link :league/by-eid} :uuid]
     [:type [:= :league/league]]
     [:name {:min 1} :string]
     [:description {:min 1} :string]
     [:game-eid {:model/link :game/by-eid} :uuid]
     [:created-by-sub :string]
     [:_links
      [:map
       [:self :url]
       [:game :url]
       [:seasons :url]
       [:faction-standings :url]]]])))

(def league-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource league-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/league]]])))

(def create-league-specification
  (schema.contract/to-schema
   [:map
    [:game-eid :uuid]
    [:name {:min 1} :string]
    [:description {:min 1} :string]]))

(def league-error-response
  (schema.contract/to-schema
   [:map
    [:type [:= :league/error]]
    [:message :string]]))

(def season-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     {:model/sub-resources {:faction-standings :collection/faction-standings}}
     [:eid {:model/link :season/by-eid} :uuid]
     [:type [:= :season/season]]
     [:league-eid {:model/link :league/by-eid} :uuid]
     [:ordinal :int]
     [:display-name :string]
     [:name [:maybe :string]]
     [:start-at :instant]
     [:end-at :instant]
     [:_links
      [:map
       [:self :url]
       [:league :url]
       [:faction-standings :url]]]])))

(def season-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource season-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/season]]])))

(def create-season-specification
  (schema.contract/to-schema
   [:map
    [:league-eid {:optional true} :uuid]
    [:name {:optional true} [:maybe :string]]
    [:timezone :timezone-id]
    [:start-at :local-datetime]
    [:end-at :local-datetime]]))

(def season-error-response
  (schema.contract/to-schema
   [:map
    [:type [:= :season/error]]
    [:message :string]]))

(def faction-standings-row-resource
  (schema.contract/to-schema
   [:map {:model/type :model/model}
    [:type [:= :stats/faction]]
    [:faction-eid {:model/link :faction/by-eid} :uuid]
    [:faction-name :string]
    [:matches-played :int]
    [:wins :int]
    [:losses :int]
    [:win-percentage :int]
    [:_links [:map [:faction :url]]]]))

(def faction-standings-response
  (schema.contract/to-schema
   [:map {:model/type :model/model}
    [:type [:= :collection/faction-standings]]
    [:game-eid {:optional true :model/link :game/by-eid} :uuid]
    [:league-eid {:optional true :model/link :league/by-eid} :uuid]
    [:season-eid {:optional true :model/link :season/by-eid} :uuid]
    [:rows [:sequential faction-standings-row-resource]]
    [:_links [:map
              [:self :url]
              [:game {:optional true} :url]
              [:league {:optional true} :url]
              [:season {:optional true} :url]]]]))
