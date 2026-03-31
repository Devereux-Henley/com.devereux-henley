(ns com.devereux-henley.rts-data-access.query.social-media
  (:require
   [com.devereux-henley.jdbc.contract :as jdbc.contract]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract])
  (:import
   [java.sql Connection]))

(defn get-platform-by-eid
  {:malli/schema (schema.contract/to-schema
                  [:=> [:cat [:instance Connection] :uuid] schema/platform-entity])}
  [connection eid]
  (jdbc.contract/entity-by-eid connection :social_media_platform eid schema/platform-entity))
