(ns com.devereux-henley.rts-domain.handlers.tournament
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.rules.tournament :as rules]
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

(defn available-transitions
  "Returns the set of valid target statuses for a tournament."
  [dependencies tournament-eid]
  (let [state (get-tournament-state dependencies tournament-eid)]
    (rules/available-transitions (:status state))))

;; ─── State machine ──────────────────────────────────────────────────────────

(defn advance-tournament
  "Advances a tournament to the target status. Only the organizer (created-by-sub)
   can advance. When transitioning to 'active', populates standings from entries."
  [dependencies tournament-eid target-status player-sub]
  (let [tournament (get-tournament-by-eid dependencies tournament-eid)]
    (cond
      (nil? tournament)
      {:type :tournament/advance-error :message "Tournament not found."}

      (not= player-sub (:created-by-sub tournament))
      {:type :tournament/advance-error :message "Only the tournament organizer can advance the tournament."}

      :else
      (let [state  (get-tournament-state dependencies tournament-eid)
            error  (rules/validate-transition (:status state) target-status)]
        (if error
          error
          (let [new-state (if (= "active" target-status)
                            (let [entries (get-entries dependencies tournament-eid)]
                              (rules/close-registration state entries))
                            (assoc state :status target-status))]
            (set-tournament-state dependencies tournament-eid new-state)
            {:type  :tournament/advance-success
             :state new-state}))))))

(defn close-registration-early
  "Sets the closed-early flag on the tournament state, preventing new entries.
   Only the organizer can close registration early."
  [dependencies tournament-eid player-sub]
  (let [tournament (get-tournament-by-eid dependencies tournament-eid)]
    (cond
      (nil? tournament)
      {:type :tournament/advance-error :message "Tournament not found."}

      (not= player-sub (:created-by-sub tournament))
      {:type :tournament/advance-error :message "Only the tournament organizer can close registration."}

      :else
      (let [state (get-tournament-state dependencies tournament-eid)]
        (if (not= "registration" (:status state))
          {:type :tournament/advance-error :message "Tournament is not in registration status."}
          (let [new-state (assoc-in state [:registration :closed-early] true)]
            (set-tournament-state dependencies tournament-eid new-state)
            {:type  :tournament/close-registration-success
             :state new-state}))))))

;; ─── Matches ─────────────────────────────────────────────────────────────────

(defn create-match
  "Creates a match within a tournament. Tournament must be active."
  [dependencies tournament-eid match-spec]
  (let [state (get-tournament-state dependencies tournament-eid)]
    (if (not= "active" (:status state))
      {:type :tournament/match-error :message "Tournament must be active to create matches."}
      (let [match (db/create-match (:connection dependencies) tournament-eid match-spec)]
        (assoc match :type :tournament/match)))))

(defn get-match-by-eid
  "Fetches a match by eid."
  [dependencies match-eid]
  (some-> (db/get-match-by-eid (:connection dependencies) match-eid)
          (assoc :type :tournament/match)))

(defn get-matches-for-tournament
  "Returns all matches for a tournament."
  [dependencies tournament-eid]
  (mapv (fn [m] (assoc m :type :tournament/match))
        (db/get-matches-for-tournament (:connection dependencies) tournament-eid)))

(defn get-matches-for-round
  "Returns matches for a specific phase and round."
  [dependencies tournament-eid phase-index round-index]
  (mapv (fn [m] (assoc m :type :tournament/match))
        (db/get-matches-for-round (:connection dependencies) tournament-eid phase-index round-index)))

(defn update-match-result
  "Records a match result and recalculates standings in the state blob."
  [dependencies match-eid winner-sub]
  (let [match (db/get-match-by-eid (:connection dependencies) match-eid)]
    (cond
      (nil? match)
      {:type :tournament/match-error :message "Match not found."}

      (not= "pending" (:status match))
      {:type :tournament/match-error :message "Match is not pending."}

      :else
      (do
        (db/update-match-result (:connection dependencies) match-eid winner-sub)
        (let [tournament-eid (:tournament-eid match)
              state          (get-tournament-state dependencies tournament-eid)
              all-matches    (get-matches-for-tournament dependencies tournament-eid)
              completed      (filter #(= "complete" (:status %)) all-matches)
              new-standings  (rules/recalculate-standings (:standings state) completed)
              new-state      (assoc state :standings new-standings)]
          (set-tournament-state dependencies tournament-eid new-state)
          {:type      :tournament/match-result-recorded
           :match-eid match-eid
           :standings new-standings})))))

;; ─── Games ───────────────────────────────────────────────────────────────────

(defn record-game-result
  "Records a game within a match. If the match win threshold is reached,
   the match auto-completes and standings are recalculated."
  [dependencies match-eid winner-sub]
  (let [match (db/get-match-by-eid (:connection dependencies) match-eid)]
    (cond
      (nil? match)
      {:type :tournament/match-error :message "Match not found."}

      (= "complete" (:status match))
      {:type :tournament/match-error :message "Match is already complete."}

      :else
      (let [existing-games (db/get-games-for-match (:connection dependencies) match-eid)
            game-index     (count existing-games)
            _              (db/create-game (:connection dependencies) match-eid game-index winner-sub)
            all-games      (conj (vec existing-games) {:winner-sub winner-sub})
            match-winner   (rules/check-match-complete all-games (:format match))]
        (if match-winner
          ;; Match complete — update match and recalculate standings
          (do
            (db/update-match-result (:connection dependencies) match-eid match-winner)
            (let [tournament-eid (:tournament-eid match)
                  state          (get-tournament-state dependencies tournament-eid)
                  all-matches    (get-matches-for-tournament dependencies tournament-eid)
                  completed      (filter #(= "complete" (:status %)) all-matches)
                  new-standings  (rules/recalculate-standings (:standings state) completed)
                  new-state      (assoc state :standings new-standings)]
              (set-tournament-state dependencies tournament-eid new-state)
              {:type         :tournament/match-completed
               :match-eid    match-eid
               :winner-sub   match-winner
               :game-index   game-index
               :standings    new-standings}))
          ;; Match still in progress
          {:type       :tournament/game-recorded
           :match-eid  match-eid
           :game-index game-index
           :winner-sub winner-sub})))))

(defn get-games-for-match
  "Returns all games for a match."
  [dependencies match-eid]
  (db/get-games-for-match (:connection dependencies) match-eid))

;; ─── Phase management ────────────────────────────────────────────────────────

(defn configure-phases
  "Sets the phase configuration on a tournament. Must be in registration status."
  [dependencies tournament-eid phase-config player-sub]
  (let [tournament (get-tournament-by-eid dependencies tournament-eid)]
    (cond
      (nil? tournament)
      {:type :tournament/phase-error :message "Tournament not found."}

      (not= player-sub (:created-by-sub tournament))
      {:type :tournament/phase-error :message "Only the tournament organizer can configure phases."}

      :else
      (let [state (get-tournament-state dependencies tournament-eid)]
        (if (not= "registration" (:status state))
          {:type :tournament/phase-error :message "Phases can only be configured during registration."}
          (let [new-state (-> state
                              (assoc :phases (:phases phase-config))
                              (assoc :qualifier-count (:qualifier-count phase-config)))]
            (set-tournament-state dependencies tournament-eid new-state)
            {:type  :tournament/phase-configured
             :state new-state}))))))

(defn- generate-pairings
  "Dispatches pairing generation based on phase type."
  [phase-type standings all-matches qualified-players]
  (case phase-type
    "swiss"              (rules/swiss-pair standings all-matches)
    "round-robin"        (rules/swiss-pair standings all-matches) ;; same pairing logic, different semantics
    "single-elimination" (rules/generate-elimination-bracket qualified-players)
    "double-elimination" (rules/generate-elimination-bracket qualified-players)
    ;; Default: Swiss-style pairing
    (rules/swiss-pair standings all-matches)))

(defn generate-next-round
  "Generates the next round of matches for the current phase.
   Dispatches pairing strategy based on phase-type."
  [dependencies tournament-eid player-sub]
  (let [tournament (get-tournament-by-eid dependencies tournament-eid)]
    (cond
      (nil? tournament)
      {:type :tournament/phase-error :message "Tournament not found."}

      (not= player-sub (:created-by-sub tournament))
      {:type :tournament/phase-error :message "Only the tournament organizer can generate rounds."}

      :else
      (let [state       (get-tournament-state dependencies tournament-eid)
            phase-idx   (:current-phase state)
            phase       (get-in state [:phases phase-idx])
            all-matches (get-matches-for-tournament dependencies tournament-eid)
            ;; Find the next round index for this phase
            phase-matches (filter #(= phase-idx (:phase-index %)) all-matches)
            next-round  (if (empty? phase-matches)
                          0
                          (inc (apply max (map :round-index phase-matches))))
            rounds      (:rounds phase)
            round-config (get rounds next-round)]
        (cond
          (nil? phase)
          {:type :tournament/phase-error :message "No active phase configured."}

          (nil? round-config)
          {:type :tournament/phase-error :message "All rounds for this phase have been generated."}

          ;; Check if previous round is complete
          (and (pos? next-round)
               (some #(= "pending" (:status %))
                     (filter #(= (dec next-round) (:round-index %)) phase-matches)))
          {:type :tournament/phase-error :message "Previous round has pending matches."}

          :else
          (let [format          (or (:format round-config) 1)
                qualifier-count (or (:qualifier-count state) (count (:standings state)))
                qualified       (->> (:standings state)
                                     (sort-by :points >)
                                     (take qualifier-count)
                                     (mapv :player-sub))
                pairings        (generate-pairings (:phase-type phase) (:standings state) all-matches qualified)
                matches         (mapv (fn [pairing]
                                        (db/create-match
                                         (:connection dependencies)
                                         tournament-eid
                                         (assoc pairing
                                                :phase-index phase-idx
                                                :round-index next-round
                                                :format format)))
                                      pairings)
                new-state       (update-in state [:phases phase-idx :rounds next-round]
                                           assoc :match-eids (mapv :eid matches)
                                           :status "active")]
            (set-tournament-state dependencies tournament-eid new-state)
            {:type    :tournament/round-generated
             :round   next-round
             :matches matches}))))))

(defn advance-phase
  "Advances to the next phase in the tournament. Generates the first round
   of the next phase using its phase-type to determine pairing strategy.
   For elimination-style phases, seeds from standings using qualifier-count."
  [dependencies tournament-eid player-sub]
  (let [tournament (get-tournament-by-eid dependencies tournament-eid)]
    (cond
      (nil? tournament)
      {:type :tournament/phase-error :message "Tournament not found."}

      (not= player-sub (:created-by-sub tournament))
      {:type :tournament/phase-error :message "Only the tournament organizer can advance the phase."}

      :else
      (let [state           (get-tournament-state dependencies tournament-eid)
            current-idx     (or (:current-phase state) 0)
            next-idx        (inc current-idx)
            next-phase      (get-in state [:phases next-idx])]
        (if (nil? next-phase)
          {:type :tournament/phase-error :message "No more phases configured."}
          (let [qualifier-count (or (:qualifier-count state) (count (:standings state)))
                qualified       (->> (:standings state)
                                     (sort-by :points >)
                                     (take qualifier-count)
                                     (mapv :player-sub))
                all-matches     (get-matches-for-tournament dependencies tournament-eid)
                format          (or (get-in next-phase [:rounds 0 :format]) 1)
                pairings        (generate-pairings (:phase-type next-phase) (:standings state) all-matches qualified)
                matches         (mapv (fn [pairing]
                                        (db/create-match
                                         (:connection dependencies)
                                         tournament-eid
                                         (assoc pairing
                                                :phase-index next-idx
                                                :round-index 0
                                                :format format)))
                                      pairings)
                new-state       (assoc state :current-phase next-idx)]
            (set-tournament-state dependencies tournament-eid new-state)
            {:type    :tournament/phase-advanced
             :phase   next-idx
             :matches matches}))))))
