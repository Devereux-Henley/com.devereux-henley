(ns com.devereux-henley.rts-domain.handlers.stats
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]))

(defn- tag-rows
  [rows]
  (mapv #(assoc % :type :stats/faction-row) rows))

(defn get-game-faction-standings
  [dependencies game-eid]
  {:type      :stats/game-faction-standings
   :scope     "game"
   :scope-eid game-eid
   :rows      (tag-rows (db/get-faction-standings-for-game (:connection dependencies) game-eid))})

(defn get-league-faction-standings
  [dependencies league-eid]
  {:type      :stats/league-faction-standings
   :scope     "league"
   :scope-eid league-eid
   :rows      (tag-rows (db/get-faction-standings-for-league (:connection dependencies) league-eid))})

(defn get-season-faction-standings
  [dependencies season-eid]
  {:type      :stats/season-faction-standings
   :scope     "season"
   :scope-eid season-eid
   :rows      (tag-rows (db/get-faction-standings-for-season (:connection dependencies) season-eid))})
