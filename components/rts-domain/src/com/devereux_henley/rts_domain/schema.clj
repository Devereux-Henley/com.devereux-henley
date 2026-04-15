(ns com.devereux-henley.rts-domain.schema
  (:require
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
       [:units-by-category {:optional true} [:sequential :any]]]]])))

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
    [:type [:= :draft/add-error]]
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
    [:icon-key {:optional true} [:maybe :string]]]))

(def draft-ability
  (schema.contract/to-schema
   [:map
    [:key :string]
    [:name :string]
    [:eid {:optional true} [:maybe :uuid]]
    [:description {:optional true} [:maybe :string]]
    [:cost :int]]))

(def draft-spell
  (schema.contract/to-schema
   [:map
    [:key :string]
    [:eid {:optional true} [:maybe :uuid]]
    [:name :string]
    [:mana-cost :int]
    [:cost :int]]))

(def draft-mount
  (schema.contract/to-schema
   [:map
    [:eid :uuid]
    [:key :string]
    [:name :string]
    [:cost :int]
    [:icon-key {:optional true} [:maybe :string]]]))

(def draft-unit-response
  (schema.contract/to-schema
   [:map
    [:type [:= :draft/unit]]
    [:draft-eid :uuid]
    [:reinforcements-enabled :boolean]
    [:items {:optional true} [:sequential draft-item]]
    [:mounts {:optional true} [:sequential draft-mount]]
    [:passive-spells {:optional true} [:sequential draft-spell]]
    [:draftable-spells {:optional true} [:sequential draft-spell]]
    [:has-passives {:optional true} :boolean]
    [:unit
     [:map
      [:eid :uuid]
      [:game-eid :uuid]
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
      [:draftable-abilities {:optional true} [:sequential draft-ability]]]]]))

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
    [:lord-unit {:optional true} :any]
    [:non-lord-units [:sequential :any]]
    [:section-units [:sequential :any]]
    [:oob {:optional true} :boolean]]))

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

(def draft-mutation-response
  (schema.contract/to-schema
   [:map
    [:type [:enum :draft/add-success :draft/remove-success]]
    [:main-section draft-section-context]
    [:reinf-section {:optional true} [:maybe draft-section-context]]]))

