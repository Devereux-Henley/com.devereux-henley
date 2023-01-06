(ns com.devereux-henley.rts-api.db.core
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rts-api.db.result-set :as db.result-set]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core]
   [next.jdbc :as jdbc]
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
   (jdbc/execute-one! connection query db.result-set/default-options)
   schema.contract/sqlite-transformer))

(defn query-for-entities
  [connection query entity-schema]
  (malli.core/decode
   [:vector entity-schema]
   (jdbc/execute! connection query db.result-set/default-options)
   schema.contract/sqlite-transformer))

(def insert! jdbc.sql/insert!)

(def execute! jdbc/execute!)

(def execute-one! jdbc/execute-one!)

(defn load-query-resource
  [domain file-name]
  (slurp (io/resource (str "rts-api/sql/" domain "/" file-name))))
