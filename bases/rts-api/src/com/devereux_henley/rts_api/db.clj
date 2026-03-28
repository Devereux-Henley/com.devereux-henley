(ns com.devereux-henley.rts-api.db
  (:require
   [integrant.core]
   [next.jdbc :as jdbc]
   [clojure.java.io :as io]))

(def db
  {:dbtype "sqlite"
   :dbname "db/database.db"})

(defn load-schema
  [file-name]
  (slurp (io/resource (str "rts-api/sql/schema/" file-name))))

(defn load-seed
  [file-name]
  (slurp (io/resource (str "rts-api/sql/seed/" file-name))))

(def game-table-query
  (load-schema "create-game-table.sql"))

(def faction-table-query
  (load-schema "create-faction-table.sql"))

(def social-media-platform-table-query
  (load-schema "create-social-media-platform-table.sql"))

(def game-social-link-table-query
  (load-schema "create-game-social-link-table.sql"))

(def tournament-table-query
  (load-schema "create-tournament-table.sql"))

(def tournament-snapshot-table-query
  (load-schema "create-tournament-snapshot-table.sql"))

(def unit-type-table-query
  (load-schema "create-unit-type-table.sql"))

(def unit-category-table-query
  (load-schema "create-unit-category-table.sql"))

(def unit-table-query
  (load-schema "create-unit-table.sql"))

(def seed-games-query
  (load-seed "seed-games.sql"))

(def seed-factions-query
  (load-seed "seed-factions.sql"))

(def seed-social-media-platforms-query
  (load-seed "seed-social-media-platforms.sql"))

(def seed-game-social-links-query
  (load-seed "seed-game-social-links.sql"))

(def seed-unit-types-query
  (load-seed "seed-unit-types.sql"))

(def seed-unit-categories-query
  (load-seed "seed-unit-categories.sql"))

(def seed-empire-units-query
  (load-seed "seed-empire-units.sql"))

(def schema-queries
  [game-table-query
   faction-table-query
   social-media-platform-table-query
   game-social-link-table-query
   tournament-table-query
   tournament-snapshot-table-query
   unit-type-table-query
   unit-category-table-query
   unit-table-query])

(def seed-queries
  [seed-games-query
   seed-factions-query
   seed-social-media-platforms-query
   seed-game-social-links-query
   seed-unit-types-query
   seed-unit-categories-query
   seed-empire-units-query])

(defn create-db
  "Creates all tables."
  []
  (let [conn (jdbc/get-connection db)]
    (doseq [query schema-queries]
      (jdbc/execute! conn [query]))))

(defn seed-db
  "Seeds the database with baseline data."
  []
  (let [conn (jdbc/get-connection db)]
    (doseq [query seed-queries]
      (jdbc/execute! conn [query]))))

(defmethod integrant.core/init-key ::connection
  [_init-key _dependencies]
  (jdbc/get-connection db))

(comment
  (create-db)
  (seed-db))
