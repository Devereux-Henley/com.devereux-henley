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

(def faction-table-query
  (slurp (io/resource "rts-api/sql/schema/create-faction-table.sql")))

(def social-media-platform-table-query
  (slurp (io/resource "rts-api/sql/schema/create-social-media-platform-table.sql")))

(def game-social-link-table-query
  (slurp (io/resource "rts-api/sql/schema/create-game-social-link-table.sql")))

(def seed-games-query
  (slurp (io/resource "rts-api/sql/seed/seed-games.sql")))

(def seed-factions-query
  (slurp (io/resource "rts-api/sql/seed/seed-factions.sql")))

(def seed-social-media-platforms-query
  (slurp (io/resource "rts-api/sql/seed/seed-social-media-platforms.sql")))

(def seed-game-social-links-query
  (slurp (io/resource "rts-api/sql/seed/seed-game-social-links.sql")))

(defn create-db
  "create db and table"
  []
  (let [conn (jdbc/get-connection db)]
    (jdbc/execute! conn [game-table-query])
    (jdbc/execute! conn [faction-table-query])
    (jdbc/execute! conn [social-media-platform-table-query])
    (jdbc/execute! conn [game-social-link-table-query])
    (jdbc/execute! conn [seed-games-query])
    (jdbc/execute! conn [seed-factions-query])
    (jdbc/execute! conn [seed-social-media-platforms-query])
    (jdbc/execute! conn [seed-game-social-links-query])))

(defmethod integrant.core/init-key ::connection
  [_init-key _dependencies]
  (jdbc/get-connection db))

(comment
  (create-db))
