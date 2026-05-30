(ns com.devereux-henley.rts-domain.handlers.game
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.handlers.draft :as handlers.draft]))

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

(defn- ->ability-row
  [a]
  {:eid (:eid a) :name (:name a) :description (:description a)})

(defn- ->spell-row
  [s]
  {:eid (:eid s) :name (or (:name s) (:key s)) :mana-cost (:mana-cost s) :cost (:cost s)})

(defn unit-view-model
  "Builds the unit detail view-model from a single `db/unit-detail` fetch:
   the unit fields, the parsed statline, and resolved abilities, draftable
   spells, mounts, and items projected to their template shapes. Returns a
   `:missing/resource` marker when the unit doesn't exist so the web layer
   renders a 404."
  [dependencies eid]
  (if-let [unit (db/unit-detail (:datalog-connection dependencies) eid)]
    (let [{:keys [stats]} (handlers.draft/parse-unit-statistics (:unit-statistics unit))]
      (assoc unit
             :type             :game/unit
             :unit-statistics  stats
             :abilities        (not-empty (mapv ->ability-row (:abilities unit)))
             :draftable-spells (not-empty (mapv ->spell-row (:draftable-spells unit)))
             :mounts           (not-empty (:mounts unit))
             :items            (not-empty (:items unit))))
    {:type :missing/resource :name "unit" :id eid}))
