(ns com.devereux-henley.rts-api.db.core
  (:require
   [com.devereux-henley.rts-api.db.result-set :as db.result-set]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core]
   [next.jdbc.sql :as jdbc.sql]))

(defn entity-by-eid
  [connection table-name eid entity-schema]
  (malli.core/decode
   entity-schema
   (jdbc.sql/get-by-id connection table-name eid :eid db.result-set/default-options)
   schema.contract/sqlite-transformer))

(defn query-for-entity
  [connection query entity-schema]
  (malli.core/decode
   entity-schema
   (jdbc.sql/query connection query db.result-set/default-options)
   schema.contract/sqlite-transformer))

(defn query-for-entities
  [connection query entity-schema]
  (query-for-entity connection query [:vector entity-schema]))
