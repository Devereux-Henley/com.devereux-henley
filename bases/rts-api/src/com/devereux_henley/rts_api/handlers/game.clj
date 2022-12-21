(ns com.devereux-henley.rts-api.handlers.game
  (:require
   [com.devereux-henley.rts-api.db.game :as db.game]
   [integrant.core]))

(defn get-game-by-id
  [dependencies id]
  (assoc (db.game/get-game-by-id (:connection dependencies) id) :type :game/game))

(defn get-games
  [dependencies]
  (mapv (fn [game] (assoc game :type :game/game)) (db.game/get-games (:connection dependencies))))
