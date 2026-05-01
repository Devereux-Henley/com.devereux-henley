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
  (slurp (io/resource (str "rts-data/sql/seed/" file-name)) :encoding "UTF-8"))

(def seed-files
  ["seed-games.sql"
   "seed-factions.sql"
   "seed-subfactions.sql"
   "seed-social-media-platforms.sql"
   "seed-game-social-links.sql"
   "seed-unit-types.sql"
   "seed-unit-categories.sql"
   "seed-empire-units.sql"
   "seed-beastmen-units.sql"
   "seed-bretonnia-units.sql"
   "seed-chaos-dwarfs-units.sql"
   "seed-daemons-of-chaos-units.sql"
   "seed-dark-elves-units.sql"
   "seed-dwarfs-units.sql"
   "seed-grand-cathay-units.sql"
   "seed-greenskins-units.sql"
   "seed-high-elves-units.sql"
   "seed-khorne-units.sql"
   "seed-kislev-units.sql"
   "seed-lizardmen-units.sql"
   "seed-norsca-units.sql"
   "seed-nurgle-units.sql"
   "seed-ogre-kingdoms-units.sql"
   "seed-skaven-units.sql"
   "seed-slaanesh-units.sql"
   "seed-tomb-kings-units.sql"
   "seed-tzeentch-units.sql"
   "seed-vampire-coast-units.sql"
   "seed-vampire-counts-units.sql"
   "seed-warriors-of-chaos-units.sql"
   "seed-wood-elves-units.sql"
   "seed-game-modes.sql"
   "seed-attributes.sql"
   "seed-abilities.sql"
   "seed-spells.sql"
   "seed-lores.sql"
   "seed-spell-lores.sql"
   "seed-items.sql"
   "seed-unit-items.sql"
   "seed-mounts.sql"
   "seed-unit-mounts.sql"
   "seed-unit-lores.sql"
   "seed-unit-keys.sql"
   "seed-unit-level-cost.sql"])

(defn seed-db
  "Seeds the database with baseline data. Reads each seed file on every call so
  REPL-driven reseeds always pick up the current files on disk."
  [db-spec]
  (let [conn (jdbc/get-connection db-spec)]
    (doseq [file-name seed-files]
      (jdbc/execute! conn [(load-seed file-name)]))))
