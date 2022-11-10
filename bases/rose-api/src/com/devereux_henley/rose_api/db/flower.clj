(ns com.devereux-henley.rose-api.db.flower
  (:require
   [com.devereux-henley.rose-api.schema :as schema]
   [next.jdbc :as jdbc]
   [next.jdbc.sql :as jdbc.sql])
  (:import
   [java.sql Connection]))

(def flower-entity
  [:map
   [:id :int]
   [:eid :uuid]
   [:name {:min 1} :string]
   [:version :int]
   [:created-at :instant]
   [:updated-at :instant]
   [:deleted-at [:maybe :instant]]])

(def create-flower-entity-specification
  [:map
   [:eid :uuid]
   [:name {:min 1} :string]
   [:created-at :instant]])

(defn get-flower-by-id
  [connection eid]
  {:malli/schema (schema/to-schema [:=> [:cat [:instance Connection] :uuid] flower-entity])}
  (jdbc.sql/get-by-id connection :flower eid :eid {}))

(defn create-flower
  {:malli/schema (schema/to-schema [:=> [:cat [:instance Connection] create-flower-entity-specification] flower-entity])}
  [connection create-specification]
  (jdbc.sql/insert! connection :flower create-specification))

(defn get-flowers-by-user-id
  {:malli/schema (schema/to-schema [:=> [:cat [:instance Connection] :uuid] [:sequential flower-entity]])}
  [connection user_eid]
  (if-let [user (jdbc.sql/get-by-id connection :user user_eid :eid {})]
    (jdbc.sql/find-by-keys connection :flower {:created-by-id (:id user)})
    []))
