(ns com.devereux-henley.rts-api.db.result-set
  (:require
   [next.jdbc.result-set :as jdbc.result-set])
  (:import
   [java.sql ResultSet ResultSetMetaData]
   [java.util UUID]))

(defn is-boolean-type?
  [^String column-type-name]
  (#{"BIT" "BOOL" "BOOLEAN"} column-type-name))

(defn is-eid?
  [^String column-name]
  (= "eid" column-name))

(def default-builder
  (jdbc.result-set/builder-adapter
   jdbc.result-set/as-unqualified-kebab-maps
   (fn [builder ^ResultSet result-set ^Integer index]
     (let [result-set-metadata ^ResultSetMetaData (:rsmeta builder)]
       (jdbc.result-set/read-column-by-index
        (cond
          (is-boolean-type? (.getColumnTypeName result-set-metadata index))
          (.getBoolean result-set index)

          (is-eid? (.getColumnName result-set-metadata index))
          (UUID/fromString (.getString result-set index))

          :else (.getObject result-set index))
        result-set-metadata
        index)))))
