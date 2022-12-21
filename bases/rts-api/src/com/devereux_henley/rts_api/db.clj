(ns com.devereux-henley.rts-api.db
  (:require
   [integrant.core]
   [next.jdbc :as jdbc]
   [clojure.java.io :as io]))

(def db
  {:dbtype "sqlite"
   :dbname "db/database.db"})

(def game-table-query
  (slurp (io/resource "rts-api/sql/schema/create-game-table.sql")))

(def seed-games-query
  (slurp (io/resource "rts-api/sql/seed/seed-games.sql")))

(defn create-db
  "create db and table"
  []
  (let [conn (jdbc/get-connection db)]
    (jdbc/execute! conn [game-table-query])
    (jdbc/execute! conn [seed-games-query])))

(defmethod integrant.core/init-key ::connection
  [_init-key _dependencies]
  (jdbc/get-connection db))

(comment
  (create-db))
