(ns com.devereux-henley.rts-api.db.social-media
  (:require
   [com.devereux-henley.rts-api.db.core :as db.core]
   [com.devereux-henley.schema.contract :as schema.contract])
  (:import
   [java.sql Connection]))

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

(defn get-platform-by-eid
  [connection eid]
  {:malli/schema (schema.contract/to-schema
                  [:=> [:cat [:instance Connection] :uuid] platform-entity])}
  (db.core/entity-by-eid connection :social_media_platform eid platform-entity))
