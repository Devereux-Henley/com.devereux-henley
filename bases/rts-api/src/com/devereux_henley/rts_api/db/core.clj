(ns com.devereux-henley.rts-api.db.core
  (:require
   [camel-snake-kebab.core]
   [camel-snake-kebab.extras]
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

(defn insert!
  [connection table-name values]
  (jdbc.sql/insert! connection
                    table-name
                    (camel-snake-kebab.extras/transform-keys
                     camel-snake-kebab.core/->snake_case_keyword
                     values)
                    db.result-set/default-options))

(defn execute!
  [connection parameters]
  (jdbc/execute! connection parameters db.result-set/default-options))

(defn execute-one!
  [connection parameters]
  (jdbc/execute-one! connection parameters db.result-set/default-options))

(defn load-query-resource
  [domain file-name]
  (slurp (io/resource (str "rts-api/sql/" domain "/" file-name))))
