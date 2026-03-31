(ns com.devereux-henley.rts-data.contract
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rts-data.migrations :as migrations]
   [integrant.core]
   [next.jdbc :as jdbc]))

(def migration-dir "rts-data/migrations")

(defmethod integrant.core/init-key ::migrate
  [_init-key opts]
  (migrations/migrate! opts)
  opts)

(defmethod integrant.core/halt-key! ::migrate
  [_halt-key _state]
  nil)

(defn- load-seed
  [file-name]
  (slurp (io/resource (str "rts-data/sql/seed/" file-name))))

(def seed-queries
  [(load-seed "seed-games.sql")
   (load-seed "seed-factions.sql")
   (load-seed "seed-social-media-platforms.sql")
   (load-seed "seed-game-social-links.sql")
   (load-seed "seed-unit-types.sql")
   (load-seed "seed-unit-categories.sql")
   (load-seed "seed-empire-units.sql")])

(defn seed-db
  "Seeds the database with baseline data."
  [db-spec]
  (let [conn (jdbc/get-connection db-spec)]
    (doseq [query seed-queries]
      (jdbc/execute! conn [query]))))
