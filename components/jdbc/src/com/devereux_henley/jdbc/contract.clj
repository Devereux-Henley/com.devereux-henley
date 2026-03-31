(ns com.devereux-henley.jdbc.contract
  (:require
   [camel-snake-kebab.core]
   [camel-snake-kebab.extras]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as jdbc.result-set]
   [next.jdbc.sql :as jdbc.sql]))

(def ^:private default-options
  {:builder-fn jdbc.result-set/as-unqualified-kebab-maps})

(defn entity-by-eid
  [connection table-name eid entity-schema]
  (malli.core/decode
   entity-schema
   (jdbc.sql/get-by-id connection table-name eid :eid default-options)
   schema.contract/sqlite-transformer))

(defn query-for-entity
  [connection query entity-schema]
  (malli.core/decode
   entity-schema
   (jdbc/execute-one! connection query default-options)
   schema.contract/sqlite-transformer))

(defn query-for-entities
  [connection query entity-schema]
  (malli.core/decode
   [:vector entity-schema]
   (jdbc/execute! connection query default-options)
   schema.contract/sqlite-transformer))

(defn insert!
  [connection table-name values]
  (jdbc.sql/insert! connection
                    table-name
                    (camel-snake-kebab.extras/transform-keys
                     camel-snake-kebab.core/->snake_case_keyword
                     values)
                    default-options))

(defn execute!
  [connection parameters]
  (jdbc/execute! connection parameters default-options))

(defn execute-one!
  [connection parameters]
  (jdbc/execute-one! connection parameters default-options))
