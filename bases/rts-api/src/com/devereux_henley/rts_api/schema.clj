(ns com.devereux-henley.rts-api.schema
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.util]))

(def social-media-platform-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :social-media/by-id} :uuid]
     [:name :string]
     [:description :string]
     [:platform-url :url]
     [:type [:= :social-media/platform]]])))

(def game-social-link-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :game/social-by-id} :uuid]
     [:type [:= :game/social]]
     [:url :url]
     [:_embedded {:optional true}
      [:map
       [:platform {:optional true} social-media-platform-resource]]]])))

(def game-resource
  (malli.util/merge
   schema.contract/base-resource
   (schema.contract/to-schema
    [:map
     [:eid {:model/link :game/by-id} :uuid]
     [:type [:= :game/game]]
     [:name :string]
     [:description :string]
     [:_embedded {:optional true}
      [:map
       [:socials [:sequential game-social-link-resource]]]]])))

(def game-collection-resource
  (malli.util/merge
   (schema.contract/make-collection-resource game-resource)
   (schema.contract/to-schema
    [:map
     [:type [:= :collection/game]]])))
