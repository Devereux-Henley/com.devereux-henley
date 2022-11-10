(ns com.devereux-henley.rose-api.db
  (:require
   [integrant.core]
   [next.jdbc :as jdbc]))

(def db
  {:dbtype      "sqlite"
   :dbname      "db/database.db"})

(def user-table-query
  "create table user (
    eid TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  )")

(def flower-table-query
  "create table flower (
    eid TEXT NOT NULL,
    name TEXT NOT NULL,
    version INT NOT NULL,
    created_by_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    deleted_at TEXT
  )")

(defn create-db
  "create db and table"
  []
  (let [conn (jdbc/get-connection db)]
    (jdbc/execute! conn [user-table-query])
    (jdbc/execute! conn [flower-table-query])))

(defmethod integrant.core/init-key ::connection
  [_init-key _dependencies]
  (jdbc/get-connection db))
