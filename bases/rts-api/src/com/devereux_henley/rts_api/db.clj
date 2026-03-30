(ns com.devereux-henley.rts-api.db
  (:require
   [integrant.core]
   [next.jdbc :as jdbc]
   [clojure.java.io :as io]))

(def db
  {:dbtype "sqlite"
   :dbname "db/database.db"})

(def db-spec
  {:connection-uri "jdbc:sqlite:db/database.db"})

(defn load-seed
  [file-name]
  (slurp (io/resource (str "rts-api/sql/seed/" file-name))))

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

(def seed-queries
  [seed-games-query
   seed-factions-query
   seed-social-media-platforms-query
   seed-game-social-links-query
   seed-unit-types-query
   seed-unit-categories-query
   seed-empire-units-query])

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
  (seed-db))
