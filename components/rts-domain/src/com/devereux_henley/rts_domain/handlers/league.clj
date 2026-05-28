(ns com.devereux-henley.rts-domain.handlers.league
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]))

(defn get-league-by-eid
  "Fetches a league by eid and tags it with :type :league/league."
  [dependencies eid]
  (some-> (db/league-by-eid (:datalog-connection dependencies) eid)
          (assoc :type :league/league)))

(defn get-leagues-for-game
  "Returns all leagues for a game, each tagged with :type :league/league."
  [dependencies game-eid]
  (mapv #(assoc % :type :league/league)
        (db/leagues-for-game (:datalog-connection dependencies) game-eid)))

(defn get-leagues
  "Returns every league in the system, each tagged with :type :league/league."
  [dependencies]
  (mapv #(assoc % :type :league/league)
        (db/leagues (:datalog-connection dependencies))))

(defn create-league
  "Creates a new league. Audit columns (`:version`, `:created-at`,
  `:updated-at`) are stamped in the data-access layer."
  [dependencies create-specification]
  (-> (db/create-league! (:datalog-connection dependencies) create-specification)
      (assoc :type :league/league)))
