(ns com.devereux-henley.rts-domain.handlers.game
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]))

(defn get-game-by-eid
  [dependencies eid]
  (when-let [game (db/get-game-by-eid (:connection dependencies) eid)]
    (assoc game :type :game/game)))

(defn get-games
  [dependencies]
  (mapv (fn [game] (assoc game :type :game/game)) (db/get-games (:connection dependencies))))

(defn get-factions-for-game
  [dependencies game-eid]
  (mapv (fn [faction] (assoc faction :type :game/faction))
        (db/get-factions-for-game (:connection dependencies) game-eid)))

(defn get-factions
  [dependencies]
  (mapv (fn [faction] (assoc faction :type :game/faction))
        (db/get-factions (:connection dependencies))))

(defn get-faction-by-eid
  [dependencies eid]
  (when-let [faction (db/get-faction-by-eid (:connection dependencies) eid)]
    (assoc faction :type :game/faction)))

(defn get-socials-for-game
  [dependencies game-eid]
  (mapv (fn [social] (assoc social :type :game/social))
        (db/get-socials-for-game (:connection dependencies) game-eid)))

(defn get-socials
  [dependencies]
  (mapv (fn [social] (assoc social :type :game/social))
        (db/get-socials (:connection dependencies))))

(defn get-game-social-link-by-eid
  [dependencies eid]
  (when-let [link (db/get-game-social-link-by-eid (:connection dependencies) eid)]
    (assoc link :type :game/social)))

(defn get-units-for-game
  [dependencies game-eid]
  (mapv (fn [unit] (assoc unit :type :game/unit))
        (db/get-units-for-game (:connection dependencies) game-eid)))

(defn get-unit-by-eid
  [dependencies eid]
  (when-let [unit (db/get-unit-by-eid (:connection dependencies) eid)]
    (assoc unit :type :game/unit)))

(defn get-units-for-faction
  [dependencies faction-eid]
  (mapv (fn [unit] (assoc unit :type :game/unit))
        (db/get-units-for-faction (:connection dependencies) faction-eid)))

(defn get-units
  [dependencies]
  (mapv (fn [unit] (assoc unit :type :game/unit))
        (db/get-units (:connection dependencies))))

(defn get-game-mode-by-eid
  [dependencies eid]
  (when-let [mode (db/get-game-mode-by-eid (:connection dependencies) eid)]
    (assoc mode :type :game/game-mode)))

(defn get-game-modes-for-game
  [dependencies game-eid]
  (mapv (fn [mode] (assoc mode :type :game/game-mode))
        (db/get-game-modes-for-game (:connection dependencies) game-eid)))
