(ns com.devereux-henley.rts-domain.handlers.tournament
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.handlers.core :as handlers.core])
  (:import
   [java.time Instant]))

(defn get-tournaments
  [dependencies since size offset]
  (mapv (fn [t] (handlers.core/assign-model-type :tournament/tournament t))
        (db/get-tournaments (:connection dependencies) since size offset)))

(defn get-tournaments-by-game-eid
  [dependencies game-eid since size offset]
  (mapv (fn [t] (handlers.core/assign-model-type :tournament/tournament t))
        (db/get-tournaments-by-game-eid (:connection dependencies) game-eid since size offset)))

(defn get-tournament-by-eid
  [dependencies eid]
  (handlers.core/assign-model-type
   :tournament/tournament
   (db/get-tournament-by-eid (:connection dependencies) eid)))

(defn get-tournament-snapshot-by-eid
  [dependencies eid]
  (handlers.core/assign-model-type
   :tournament/snapshot
   (db/get-tournament-snapshot-by-eid (:connection dependencies) eid)))

(defn get-tournament-snapshot-by-tournament-eid
  [dependencies tournament-eid]
  (handlers.core/assign-model-type
   :tournament/snapshot
   (db/get-tournament-snapshot-by-tournament-eid (:connection dependencies) tournament-eid)))

;; TODO Handle missing game.
(defn create-tournament
  [dependencies create-specification]
  (let [created-at (Instant/now)
        updated-at created-at]
    (handlers.core/assign-model-type
     :tournament/tournament
     (db/create-tournament (:connection dependencies) (-> create-specification
                                                          (assoc :created-at created-at)
                                                          (assoc :updated-at updated-at))))))

(defn update-tournament-snapshot
  [dependencies tournament-eid new-snapshot]
  (handlers.core/assign-model-type
   :tournament/snapshot
   (db/update-tournament-snapshot-by-tournament-eid
    (:connection dependencies)
    tournament-eid
    new-snapshot)))
