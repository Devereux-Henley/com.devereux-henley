(ns com.devereux-henley.rts-api.schema
  (:require
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.util]))

(def game-resource
  (schema.contract/to-schema
   (malli.util/merge
    schema.contract/base-resource
    [:map
     [:eid {:model/link "/api/game"} :uuid]
     [:type [:= :game/game]]
     [:name :string]
     [:description :string]])))

(def game-collection-resource
  (schema.contract/to-schema
   (malli.util/merge
    (schema.contract/make-collection-resource game-resource)
    [:map
     [:type [:= :collection/game]]])))
