(ns com.devereux-henley.rts-domain.handlers.game
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]))

(defn get-game-by-eid
  [dependencies eid]
  (when-let [game (db/game-by-eid (:datalog-connection dependencies) eid)]
    (assoc game :type :game/game)))

(defn get-games
  [dependencies]
  (mapv (fn [game] (assoc game :type :game/game)) (db/games (:datalog-connection dependencies))))

(defn get-factions-for-game
  [dependencies game-eid]
  (mapv (fn [faction] (assoc faction :type :game/faction))
        (db/factions-for-game (:datalog-connection dependencies) game-eid)))

(defn get-factions
  [dependencies]
  (mapv (fn [faction] (assoc faction :type :game/faction))
        (db/factions (:datalog-connection dependencies))))

(defn get-faction-by-eid
  [dependencies eid]
  (when-let [faction (db/faction-by-eid (:datalog-connection dependencies) eid)]
    (assoc faction :type :game/faction)))

(defn get-socials-for-game
  [dependencies game-eid]
  (mapv (fn [social] (assoc social :type :game/social))
        (db/socials-for-game (:datalog-connection dependencies) game-eid)))

(defn get-units-for-game
  [dependencies game-eid]
  (mapv (fn [unit] (assoc unit :type :game/unit))
        (db/units-for-game (:datalog-connection dependencies) game-eid)))

(defn get-unit-by-eid
  [dependencies eid]
  (when-let [unit (db/unit-by-eid (:datalog-connection dependencies) eid)]
    (assoc unit :type :game/unit)))

(defn get-units-for-faction
  [dependencies faction-eid]
  (mapv (fn [unit] (assoc unit :type :game/unit))
        (db/units-for-faction (:datalog-connection dependencies) faction-eid)))

(defn get-units
  [dependencies]
  (mapv (fn [unit] (assoc unit :type :game/unit))
        (db/units (:datalog-connection dependencies))))

(defn get-game-mode-by-eid
  [dependencies eid]
  (when-let [mode (db/game-mode-by-eid (:datalog-connection dependencies) eid)]
    (assoc mode :type :game/game-mode)))

(defn get-game-modes-for-game
  [dependencies game-eid]
  (mapv (fn [mode] (assoc mode :type :game/game-mode))
        (db/game-modes-for-game (:datalog-connection dependencies) game-eid)))
