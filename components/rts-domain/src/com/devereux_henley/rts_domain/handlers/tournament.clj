(ns com.devereux-henley.rts-domain.handlers.tournament
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]
   [jsonista.core :as jsonista])
  (:import
   [java.time Instant LocalDateTime ZoneId]))

(def ^:private json-mapper (jsonista/object-mapper {:decode-key-fn keyword}))

(defn- to-utc-instant
  "Converts a LocalDateTime in the given ZoneId to a UTC Instant."
  ^Instant [^LocalDateTime local-dt ^ZoneId zone]
  (-> local-dt (.atZone zone) .toInstant))

(defn get-tournament-by-eid
  "Fetches a tournament by eid and attaches :type :tournament/tournament."
  [dependencies eid]
  (some-> (db/get-tournament-by-eid (:connection dependencies) eid)
          (assoc :type :tournament/tournament)))

(defn get-tournaments-for-game
  "Returns all tournaments for a game, each tagged with :type :tournament/tournament."
  [dependencies game-eid]
  (mapv (fn [t] (assoc t :type :tournament/tournament))
        (db/get-tournaments-for-game (:connection dependencies) game-eid)))

(defn get-tournament-state
  "Returns the parsed tournament state map, or a default initial state when none exists."
  [dependencies tournament-eid]
  (if-let [row (db/get-tournament-state (:connection dependencies) tournament-eid)]
    (jsonista/read-value (:state row) json-mapper)
    {:status "registration"
     :registration {:opens-at nil :closes-at nil :closed-early false}
     :phases []
     :current-phase nil
     :standings []
     :qualifier-count nil}))

(defn set-tournament-state
  "Persists the tournament state as a JSON blob."
  [dependencies tournament-eid state]
  (db/upsert-tournament-state
   (:connection dependencies)
   tournament-eid
   (jsonista/write-value-as-string state)))

(defn create-tournament
  "Creates a new tournament with an initial state blob."
  [dependencies create-specification]
  (let [created-at (Instant/now)
        updated-at created-at
        tz         (:timezone create-specification)
        opens-at   (to-utc-instant (:registration-opens-at create-specification) tz)
        closes-at  (to-utc-instant (:registration-closes-at create-specification) tz)
        tournament (db/create-tournament
                    (:connection dependencies)
                    (-> create-specification
                        (dissoc :registration-opens-at :registration-closes-at :timezone)
                        (assoc :created-at created-at)
                        (assoc :updated-at updated-at)))
        initial-state {:status "registration"
                       :registration {:opens-at (str opens-at)
                                      :closes-at (str closes-at)
                                      :timezone (str tz)
                                      :closed-early false}
                       :phases []
                       :current-phase nil
                       :standings []
                       :qualifier-count nil}]
    (set-tournament-state dependencies (:eid tournament) initial-state)
    (assoc tournament :type :tournament/tournament)))

;; ─── Registration ────────────────────────────────────────────────────────────

(defn is-registration-open?
  "Returns true if the tournament state allows new registrations.
   Checks status, time window, and closed-early flag."
  [state now]
  (and (= "registration" (:status state))
       (not (get-in state [:registration :closed-early]))
       (let [opens-at  (some-> (get-in state [:registration :opens-at]) Instant/parse)
             closes-at (some-> (get-in state [:registration :closes-at]) Instant/parse)]
         (and (or (nil? opens-at)  (not (.isBefore now opens-at)))
              (or (nil? closes-at) (.isBefore now closes-at))))))

(defn create-entry
  "Creates a tournament entry for a player. Returns the entry or an error map."
  [dependencies tournament-eid player-sub]
  (let [state (get-tournament-state dependencies tournament-eid)
        now   (Instant/now)]
    (if-not (is-registration-open? state now)
      {:type :tournament/entry-error :message "Registration is not open."}
      (try
        (let [entry (db/create-entry (:connection dependencies) tournament-eid player-sub)]
          (assoc entry :type :tournament/entry))
        (catch org.sqlite.SQLiteException e
          (if (.contains (.getMessage e) "UNIQUE constraint failed")
            {:type :tournament/entry-error :message "Already entered in this tournament."}
            (throw e)))))))

(defn delete-entry
  "Removes a player's entry from a tournament. Returns a success or error map."
  [dependencies tournament-eid player-sub]
  (let [state (get-tournament-state dependencies tournament-eid)]
    (if-not (= "registration" (:status state))
      {:type :tournament/entry-error :message "Cannot withdraw outside of registration period."}
      (do
        (db/delete-entry (:connection dependencies) tournament-eid player-sub)
        {:type :tournament/entry-deleted :message "Entry removed from tournament."}))))

(defn get-entries
  "Returns all active entries for a tournament."
  [dependencies tournament-eid]
  (mapv (fn [e] (assoc e :type :tournament/entry))
        (db/get-entries-for-tournament (:connection dependencies) tournament-eid)))
