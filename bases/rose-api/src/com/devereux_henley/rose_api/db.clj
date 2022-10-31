(ns com.devereux-henley.rose-api.db
  (:require
   [next.jdbc :as jdbc]))

(def db
  {:dbtype      "sqlite"
   :dbname      "db/database.db"
   })

(defn create-db
  "create db and table"
  []
  (let [conn (jdbc/get-connection db)]
    (jdbc/execute! conn ["create table flower (
                           eid text,
                           name text,
                           version int,
                           created_at text,
                           updated_at text,
                           deleted_at text
                         )"])))
