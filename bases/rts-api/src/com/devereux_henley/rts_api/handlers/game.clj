(ns com.devereux-henley.rts-api.handlers.game
  (:require
   [com.devereux-henley.rts-api.db.game :as db.game]
   [integrant.core]))

(defn get-game-by-eid
  [dependencies eid]
  (assoc (db.game/get-game-by-eid (:connection dependencies) eid) :type :game/game))

(defn get-games
  [dependencies]
  (mapv (fn [game] (assoc game :type :game/game)) (db.game/get-games (:connection dependencies))))

(defn get-factions-for-game
  [dependencies game-eid]
  (mapv (fn [faction] (assoc faction :type :game/faction))
        (db.game/get-factions-for-game (:connection dependencies) game-eid)))

(defn get-faction-by-eid
  [dependencies eid]
  (assoc (db.game/get-faction-by-eid (:connection dependencies) eid) :type :game/faction))

(defn get-socials-for-game
  [dependencies game-eid]
  (mapv (fn [social] (assoc social :type :game/social))
        (db.game/get-socials-for-game (:connection dependencies) game-eid)))

(defn get-game-social-link-by-eid
  [dependencies eid]
  (assoc (db.game/get-game-social-link-by-eid (:connection dependencies) eid) :type :game/social))
