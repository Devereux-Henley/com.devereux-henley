(ns com.devereux-henley.rts-api.db.social-media
  (:require
   [com.devereux-henley.rts-api.db.result-set :as db.result-set]
   [com.devereux-henley.schema.contract :as schema]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as jdbc.sql]
   [clojure.java.io :as io])
  (:import
   [java.sql Connection]))

(def platform-entity
  [:map
   [:id :int]
   [:eid :uuid]
   [:name {:min 1} :string]
   [:description {:min 1} :string]
   [:platform-url {:min 1} :string]
   [:version :int]
   [:created-at :instant]
   [:updated-at :instant]
   [:deleted-at [:maybe :instant]]])

(defn get-platform-by-eid
  [connection eid]
  {:malli/schema (schema/to-schema [:=> [:cat [:instance Connection] :uuid] platform-entity])}
  (jdbc.sql/get-by-id connection :social_media_platform eid :eid {:builder-fn db.result-set/default-builder}))
