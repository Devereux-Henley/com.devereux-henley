(ns com.devereux-henley.rts-domain.handlers.league
  (:require
   [com.devereux-henley.rts-data-access.contract :as db])
  (:import
   [java.time Instant]))

(defn get-league-by-eid
  "Fetches a league by eid and tags it with :type :league/league."
  [dependencies eid]
  (some-> (db/get-league-by-eid (:connection dependencies) eid)
          (assoc :type :league/league)))

(defn get-leagues-for-game
  "Returns all leagues for a game, each tagged with :type :league/league."
  [dependencies game-eid]
  (mapv #(assoc % :type :league/league)
        (db/get-leagues-for-game (:connection dependencies) game-eid)))

(defn create-league
  "Creates a new league."
  [dependencies create-specification]
  (let [now    (Instant/now)
        league (db/create-league
                (:connection dependencies)
                (-> create-specification
                    (assoc :version 1)
                    (assoc :created-at now)
                    (assoc :updated-at now)))]
    (assoc league :type :league/league)))
