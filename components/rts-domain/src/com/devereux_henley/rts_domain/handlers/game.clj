(ns com.devereux-henley.rts-domain.handlers.game
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]))

(defn get-game-by-eid
  [dependencies eid]
  (assoc (db/get-game-by-eid (:connection dependencies) eid) :type :game/game))

(defn get-games
  [dependencies]
  (mapv (fn [game] (assoc game :type :game/game)) (db/get-games (:connection dependencies))))

(defn get-factions-for-game
  [dependencies game-eid]
  (mapv (fn [faction] (assoc faction :type :game/faction))
        (db/get-factions-for-game (:connection dependencies) game-eid)))

(defn get-faction-by-eid
  [dependencies eid]
  (assoc (db/get-faction-by-eid (:connection dependencies) eid) :type :game/faction))

(defn get-socials-for-game
  [dependencies game-eid]
  (mapv (fn [social] (assoc social :type :game/social))
        (db/get-socials-for-game (:connection dependencies) game-eid)))

(defn get-game-social-link-by-eid
  [dependencies eid]
  (assoc (db/get-game-social-link-by-eid (:connection dependencies) eid) :type :game/social))

(defn get-units-for-game
  [dependencies game-eid]
  (mapv (fn [unit] (assoc unit :type :game/unit))
        (db/get-units-for-game (:connection dependencies) game-eid)))

(defn get-unit-by-eid
  [dependencies eid]
  (assoc (db/get-unit-by-eid (:connection dependencies) eid) :type :game/unit))

(defn get-units-for-faction
  [dependencies faction-eid]
  (mapv (fn [unit] (assoc unit :type :game/unit))
        (db/get-units-for-faction (:connection dependencies) faction-eid)))
