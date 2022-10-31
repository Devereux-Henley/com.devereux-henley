(ns com.devereux-henley.rose-api.db
  (:require
   [integrant.core]
   [next.jdbc :as jdbc]))

(def db
  {:dbtype      "sqlite"
   :dbname      "db/database.db"})

(defn create-db
  "create db and table"
  []
  (let [conn (jdbc/get-connection db)]
    (jdbc/execute! conn ["create table flower (
                           eid text NOT NULL,
                           name TEXT NOT NULL,
                           version INT NOT NULL,
                           created_at TEXT NOT NULL,
                           updated_at TEXT NOT NULL,
                           deleted_at TEXT
                         )"])))

(defmethod integrant.core/init-key ::connection
  [_init-key _dependencies]
  (jdbc/get-connection db))
