(ns com.devereux-henley.rts-domain.handlers.game
  (:require
   [clojure.string :as str]
   [com.devereux-henley.rts-data-access.contract :as db]
   [jsonista.core :as jsonista])
  (:import
   [java.time Instant]))

(def ^:private stat-exclude-keys #{"abilities" "draftable-spells" "draftable-abilities" "mounts"})

(defn parse-unit-statistics
  [unit-statistics-str]
  (try
    (let [stats (jsonista/read-value unit-statistics-str (jsonista/object-mapper {:decode-key-fn name}))]
      {:stats
       (into []
             (keep (fn [[k v]]
                     (when-not (stat-exclude-keys k)
                       (cond
                         (and (vector? v) (empty? v)) nil
                         (= v 0)                      nil
                         (vector? v)                  {:stat (str/replace k "_" " ") :value (str/join ", " v)}
                         :else                        {:stat (str/replace k "_" " ") :value v}))))
             stats)
       :abilities        (get stats "abilities" [])
       :draftable-spells (get stats "draftable-spells" [])
       :mounts           (mapv (fn [m] {:name    (get m "name")
                                        :mp-cost (get m "mp_cost")})
                               (get stats "mounts" []))})
    (catch Exception _
      {:stats [] :abilities [] :draftable-spells [] :mounts []})))

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

(defn get-draft-by-eid
  [dependencies eid]
  (assoc (db/get-draft-by-eid (:connection dependencies) eid) :type :game/draft))

(defn create-draft
  [dependencies create-specification]
  (let [created-at (Instant/now)
        updated-at created-at]
    (assoc (db/create-draft (:connection dependencies)
                            (-> create-specification
                                (assoc :created-at created-at)
                                (assoc :updated-at updated-at)))
           :type :game/draft)))

(defn get-drafts-for-player
  [dependencies player-sub]
  (mapv (fn [draft] (assoc draft :type :game/draft))
        (db/get-drafts-for-player (:connection dependencies) player-sub)))

(defn get-drafts-for-player-by-game
  [dependencies player-sub game-eid]
  (mapv (fn [draft] (assoc draft :type :game/draft))
        (db/get-drafts-for-player-by-game (:connection dependencies) player-sub game-eid)))

(defn get-game-mode-by-eid
  [dependencies eid]
  (assoc (db/get-game-mode-by-eid (:connection dependencies) eid) :type :game/game-mode))

(defn get-spells-by-keys
  [dependencies spell-keys]
  (db/get-spells-by-keys (:connection dependencies) spell-keys))

(defn get-game-modes-for-game
  [dependencies game-eid]
  (mapv (fn [mode] (assoc mode :type :game/game-mode))
        (db/get-game-modes-for-game (:connection dependencies) game-eid)))
