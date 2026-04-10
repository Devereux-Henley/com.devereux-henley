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
    [:value :any]
    [:percentage :int]]))

(def draft-unit-response
  (schema.contract/to-schema
   [:map
    [:type [:= :draft/unit]]
    [:draft-eid :uuid]
    [:reinforcements-enabled :boolean]
    [:unit
     [:map
      [:eid :uuid]
      [:name :string]
      [:description :string]
      [:unit-type-name :string]
      [:unit-category-name :string]
      [:cost [:maybe :int]]
      [:unit-statistics [:sequential draft-unit-stat]]
      [:parsed-abilities [:sequential [:map
                                       [:name :string]
                                       [:eid {:optional true} [:maybe :uuid]]
                                       [:description {:optional true} [:maybe :string]]]]]]]]))

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

(def draft-mutation-response
  (schema.contract/to-schema
   [:map
    [:type [:enum :draft/add-success :draft/remove-success]]
    [:main-section draft-section-context]
    [:reinf-section {:optional true} [:maybe draft-section-context]]]))

