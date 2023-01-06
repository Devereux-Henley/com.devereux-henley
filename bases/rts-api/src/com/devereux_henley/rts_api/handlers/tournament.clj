(ns com.devereux-henley.rts-api.handlers.tournament
  (:require
   [com.devereux-henley.rts-api.db.tournament :as db.tournament]
   [com.devereux-henley.rts-api.handlers.core :as handlers.core]))

(defn get-tournaments
  [dependencies since size offset]
  (mapv (fn [tournament] (handlers.core/assign-model-type :tournament/tournament tournament))
        (db.tournament/get-tournaments (:connection dependencies) since size offset)))

(defn get-tournaments-by-game-eid
  [dependencies game-eid since size offset]
  (mapv (fn [tournament] (handlers.core/assign-model-type :tournament/tournament tournament))
        (db.tournament/get-tournaments-by-game-eid (:connection dependencies)
                                                   game-eid
                                                   since
                                                   size
                                                   offset)))

(defn get-tournament-by-eid
  [dependencies eid]
  (handlers.core/assign-model-type
   :tournament/tournament
   (db.tournament/get-tournament-by-eid
    (:connection dependencies)
    eid)))

(defn get-tournament-snapshot-by-eid
  [dependencies eid]
  (handlers.core/assign-model-type
   :tournament/snapshot
   (db.tournament/get-tournament-snapshot-by-eid
    (:connection dependencies)
    eid)))

(defn get-tournament-snapshot-by-tournament-eid
  [dependencies tournament-eid]
  (handlers.core/assign-model-type
   :tournament/snapshot
   (db.tournament/get-tournament-snapshot-by-tournament-eid
    (:connection dependencies)
    tournament-eid)))

(defn create-tournament
  [dependencies create-specification]
  (handlers.core/assign-model-type
   :tournament/tournament
   (db.tournament/create-tournament (:connection dependencies) create-specification)))

(defn update-tournament-snapshot-by-tournament-eid
  [dependencies tournament-eid new-snapshot]
  (handlers.core/assign-model-type
   :tournament/snapshot
   (db.tournament/update-tournament-snapshot-by-tournament-eid
    (:connection dependencies)
    tournament-eid
    new-snapshot)))
