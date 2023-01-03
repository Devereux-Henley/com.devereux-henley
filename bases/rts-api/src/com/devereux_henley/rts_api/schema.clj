(ns com.devereux-henley.rts-api.schema
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
       [:game :url]]]])))

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
       [:socials [:sequential game-social-link-resource]]
       [:factions [:sequential faction-resource]]]]])))

(def game-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource game-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/game]]])))
