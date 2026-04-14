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

(def seed-queries
  [(load-seed "seed-games.sql")
   (load-seed "seed-factions.sql")
   (load-seed "seed-social-media-platforms.sql")
   (load-seed "seed-game-social-links.sql")
   (load-seed "seed-unit-types.sql")
   (load-seed "seed-unit-categories.sql")
   (load-seed "seed-empire-units.sql")
   (load-seed "seed-beastmen-units.sql")
   (load-seed "seed-bretonnia-units.sql")
   (load-seed "seed-chaos-dwarfs-units.sql")
   (load-seed "seed-daemons-of-chaos-units.sql")
   (load-seed "seed-dark-elves-units.sql")
   (load-seed "seed-dwarfs-units.sql")
   (load-seed "seed-grand-cathay-units.sql")
   (load-seed "seed-greenskins-units.sql")
   (load-seed "seed-high-elves-units.sql")
   (load-seed "seed-khorne-units.sql")
   (load-seed "seed-kislev-units.sql")
   (load-seed "seed-lizardmen-units.sql")
   (load-seed "seed-norsca-units.sql")
   (load-seed "seed-nurgle-units.sql")
   (load-seed "seed-ogre-kingdoms-units.sql")
   (load-seed "seed-skaven-units.sql")
   (load-seed "seed-slaanesh-units.sql")
   (load-seed "seed-tomb-kings-units.sql")
   (load-seed "seed-tzeentch-units.sql")
   (load-seed "seed-vampire-coast-units.sql")
   (load-seed "seed-vampire-counts-units.sql")
   (load-seed "seed-warriors-of-chaos-units.sql")
   (load-seed "seed-wood-elves-units.sql")
   (load-seed "seed-game-modes.sql")
   (load-seed "seed-attributes.sql")
   (load-seed "seed-abilities.sql")
   (load-seed "seed-spells.sql")
   (load-seed "seed-lores.sql")
   (load-seed "seed-spell-lores.sql")
   (load-seed "seed-items.sql")
   (load-seed "seed-unit-items.sql")])

(defn seed-db
  "Seeds the database with baseline data."
  [db-spec]
  (let [conn (jdbc/get-connection db-spec)]
    (doseq [query seed-queries]
      (jdbc/execute! conn [query]))))
