(ns com.devereux-henley.rts-api.db
  (:require
   [integrant.core]
   [next.jdbc :as jdbc]))

(def db
  {:dbtype "sqlite"
   :dbname "db/database.db"})

(def db-spec
  {:connection-uri "jdbc:sqlite:db/database.db"})

(defmethod integrant.core/init-key ::connection
  [_init-key _dependencies]
  (jdbc/get-connection db))
