(ns com.devereux-henley.rts-domain.handlers.tournament
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.rules.tournament :as rules]
   [com.devereux-henley.rts-domain.time :as time]
   [jsonista.core :as jsonista])
  (:import
   [java.time Instant]))

(def ^:private json-mapper (jsonista/object-mapper {:decode-key-fn keyword}))

(defn- tag-tournament
  [tournament]
  (some-> tournament (assoc :type :tournament/tournament)))

(defn- tag-match
  [match]
  (some-> match (assoc :type :tournament/match)))

(defn get-tournament-by-eid
  "Fetches a tournament by eid and attaches :type :tournament/tournament."
  [dependencies eid]
  (tag-tournament (db/get-tournament-by-eid (:connection dependencies) eid)))

(defn get-tournaments-for-game
  "Returns all tournaments for a game, each tagged with :type :tournament/tournament."
  [dependencies game-eid]
  (mapv tag-tournament
        (db/get-tournaments-for-game (:connection dependencies) game-eid)))

(defn get-tournament-state
  "Returns the parsed tournament state map, or a default initial state when none exists."
  [dependencies tournament-eid]
  (if-let [row (db/get-tournament-state (:connection dependencies) tournament-eid)]
    (jsonista/read-value (:state row) json-mapper)
    {:status          "registration"
     :registration    {:opens-at nil :closes-at nil :closed-early false}
     :phases          []
     :current-phase   nil
     :standings       []
     :qualifier-count nil}))

(defn set-tournament-state
  "Persists the tournament state as a JSON blob."
  [dependencies tournament-eid state]
  (db/upsert-tournament-state
   (:connection dependencies)
   tournament-eid
   (jsonista/write-value-as-string state)))

(defn create-tournament
  "Creates a new tournament with an initial state blob. When :season-eid is
   supplied, derives :league-eid from the season server-side and rejects
   any caller-supplied :league-eid that disagrees. Both keys are optional —
   tournaments may stand alone."
  [dependencies create-specification]
  (let [conn       (:connection dependencies)
        season     (when-let [seid (:season-eid create-specification)]
                     (db/get-season-by-eid conn seid))
        derived-le (cond
                     season                            (:league-eid season)
                     (:league-eid create-specification) (:league-eid create-specification)
                     :else                              nil)]
    (cond
      (and (:season-eid create-specification) (nil? season))
      {:type :tournament/create-error :message "Season not found."}

      (and (:league-eid create-specification)
           (:season-eid create-specification)
           (not= (:league-eid create-specification) (:league-eid season)))
      {:type :tournament/create-error :message "Provided league does not match the season's league."}

      :else
      (let [created-at    (Instant/now)
            updated-at    created-at
            tz            (:timezone create-specification)
            opens-at      (time/to-utc-instant (:registration-opens-at create-specification) tz)
            closes-at     (time/to-utc-instant (:registration-closes-at create-specification) tz)
            spec          (-> create-specification
                              (dissoc :registration-opens-at :registration-closes-at :timezone)
                              (assoc :created-at created-at)
                              (assoc :updated-at updated-at)
                              (cond-> derived-le (assoc :league-eid derived-le))
                              (cond-> (not derived-le) (dissoc :league-eid)))
            tournament    (db/create-tournament conn spec)
            initial-state {:status          "registration"
                           :registration    {:opens-at     (str opens-at)
                                             :closes-at    (str closes-at)
                                             :timezone     (str tz)
                                             :closed-early false}
                           :phases          []
                           :current-phase   nil
                           :standings       []
                           :qualifier-count nil}]
        (set-tournament-state dependencies (:eid tournament) initial-state)
        (tag-tournament tournament)))))

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

(defn- organizer-error
  "Returns nil if the user can act as the organizer on the given tournament,
   or an `{:type error-type :message …}` map otherwise. Used by every
   per-transition handler to short-circuit on missing tournament or
   non-organizer caller."
  [dependencies tournament-eid user-sub error-type]
  (let [tournament (get-tournament-by-eid dependencies tournament-eid)]
    (cond
      (nil? tournament)
      {:type error-type :message "Tournament not found."}

      (not= user-sub (:created-by-sub tournament))
      {:type error-type :message "Only the tournament organizer can perform this action."}

      :else nil)))

(defn start-tournament
  "Transitions a tournament from registration to active. Populates the
   standings list from current entries via `rules/close-registration`.
   Only the organizer can start. Returns `:tournament/started` on
   success, `:tournament/start-error` on failure."
  [dependencies tournament-eid user-sub]
  (or (organizer-error dependencies tournament-eid user-sub :tournament/start-error)
      (let [state (get-tournament-state dependencies tournament-eid)]
        (if (not= "registration" (:status state))
          {:type    :tournament/start-error
           :message (str "Cannot start: tournament is '" (:status state) "', not 'registration'.")}
          (let [entries   (get-entries dependencies tournament-eid)
                new-state (rules/close-registration state entries)]
            (set-tournament-state dependencies tournament-eid new-state)
            {:type :tournament/started :state new-state})))))

(defn complete-tournament
  "Transitions a tournament from active to complete. Only the organizer
   can complete. Returns `:tournament/completed` on success,
   `:tournament/complete-error` on failure."
  [dependencies tournament-eid user-sub]
  (or (organizer-error dependencies tournament-eid user-sub :tournament/complete-error)
      (let [state (get-tournament-state dependencies tournament-eid)]
        (if (not= "active" (:status state))
          {:type    :tournament/complete-error
           :message (str "Cannot complete: tournament is '" (:status state) "', not 'active'.")}
          (let [new-state (assoc state :status "complete")]
            (set-tournament-state dependencies tournament-eid new-state)
            {:type :tournament/completed :state new-state})))))

(defn cancel-tournament
  "Cancels a tournament that hasn't already finished. Only the organizer
   can cancel. Returns `:tournament/cancelled` on success,
   `:tournament/cancel-error` on failure."
  [dependencies tournament-eid user-sub]
  (or (organizer-error dependencies tournament-eid user-sub :tournament/cancel-error)
      (let [state (get-tournament-state dependencies tournament-eid)]
        (if (contains? #{"complete" "cancelled"} (:status state))
          {:type    :tournament/cancel-error
           :message (str "Cannot cancel: tournament is already '" (:status state) "'.")}
          (let [new-state (assoc state :status "cancelled")]
            (set-tournament-state dependencies tournament-eid new-state)
            {:type :tournament/cancelled :state new-state})))))

(defn close-registration-early
  "Sets the closed-early flag on a tournament still in registration,
   preventing new entries while keeping the registration window open
   for any in-flight UI. Only the organizer can close registration
   early. Returns `:tournament/registration-closed` on success,
   `:tournament/registration-close-error` on failure."
  [dependencies tournament-eid user-sub]
  (or (organizer-error dependencies tournament-eid user-sub :tournament/registration-close-error)
      (let [state (get-tournament-state dependencies tournament-eid)]
        (if (not= "registration" (:status state))
          {:type :tournament/registration-close-error :message "Tournament is not in registration status."}
          (let [new-state (assoc-in state [:registration :closed-early] true)]
            (set-tournament-state dependencies tournament-eid new-state)
            {:type :tournament/registration-closed :state new-state})))))

;; ─── Matches ─────────────────────────────────────────────────────────────────

(defn create-match
  "Creates a match within a tournament. Tournament must be active."
  [dependencies tournament-eid match-spec]
  (let [state (get-tournament-state dependencies tournament-eid)]
    (if (not= "active" (:status state))
      {:type :tournament/match-error :message "Tournament must be active to create matches."}
      (tag-match (db/create-match (:connection dependencies) tournament-eid match-spec)))))

(defn get-match-by-eid
  "Fetches a match by eid."
  [dependencies match-eid]
  (tag-match (db/get-match-by-eid (:connection dependencies) match-eid)))

(defn get-matches-for-tournament
  "Returns all matches for a tournament."
  [dependencies tournament-eid]
  (mapv tag-match
        (db/get-matches-for-tournament (:connection dependencies) tournament-eid)))

(defn get-matches-for-round
  "Returns matches for a specific phase and round."
  [dependencies tournament-eid phase-index round-index]
  (mapv tag-match
        (db/get-matches-for-round (:connection dependencies) tournament-eid phase-index round-index)))

(defn- double-elim-complete?
  "Returns true when a double-elimination phase has a completed grand-final
   match. Double-elim tournaments end when the grand-final resolves."
  [phase-index all-matches]
  (boolean
   (some (fn [m]
           (and (= phase-index (:phase-index m))
                (= "grand-final" (:bracket-type m))
                (= "complete" (:status m))))
         all-matches)))

(defn- recalculate-and-check-completion
  "Recalculates standings after a match completes. If phases are configured
   and all matches in all phases are done with no more rounds remaining,
   auto-completes the tournament. Without phase configuration, no
   auto-completion occurs."
  [dependencies tournament-eid]
  (let [state         (get-tournament-state dependencies tournament-eid)
        all-matches   (get-matches-for-tournament dependencies tournament-eid)
        completed     (filter #(= "complete" (:status %)) all-matches)
        new-standings (rules/recalculate-standings (:standings state) completed)
        has-phases    (seq (:phases state))
        complete?     (when has-phases
                        (let [all-done    (every? #(= "complete" (:status %)) all-matches)
                              phase-index (:current-phase state)
                              phase       (get-in state [:phases phase-index])
                              last-phase  (nil? (get-in state [:phases (inc phase-index)]))]
                          (cond
                            (not last-phase) false
                            (= "double-elimination" (:phase-type phase))
                            (double-elim-complete? phase-index all-matches)
                            :else
                            (let [phase-matches (filter #(= phase-index (:phase-index %)) all-matches)
                                  max-round     (if (empty? phase-matches) -1 (apply max (map :round-index phase-matches)))
                                  rounds-left   (get-in phase [:rounds (inc max-round)])]
                              (and all-done (nil? rounds-left))))))
        new-state     (cond-> (assoc state :standings new-standings)
                        complete? (assoc :status "complete"))]
    (set-tournament-state dependencies tournament-eid new-state)
    {:standings           new-standings
     :tournament-complete (boolean complete?)}))

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
        (let [{:keys [standings tournament-complete]}
              (recalculate-and-check-completion dependencies (:tournament-eid match))]
          (cond-> {:type      :tournament/match-result-recorded
                   :match-eid match-eid
                   :standings standings}
            tournament-complete (assoc :tournament-complete true)))))))

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
          ;; Match complete — update match, recalculate standings, check tournament completion
          (do
            (db/update-match-result (:connection dependencies) match-eid match-winner)
            (let [{:keys [standings tournament-complete]}
                  (recalculate-and-check-completion dependencies (:tournament-eid match))]
              (cond-> {:type       :tournament/match-completed
                       :match-eid  match-eid
                       :winner-sub match-winner
                       :game-index game-index
                       :standings  standings}
                tournament-complete (assoc :tournament-complete true))))
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
  [dependencies tournament-eid phase-config user-sub]
  (let [tournament (get-tournament-by-eid dependencies tournament-eid)]
    (cond
      (nil? tournament)
      {:type :tournament/phase-error :message "Tournament not found."}

      (not= user-sub (:created-by-sub tournament))
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
  "Dispatches pairing generation based on phase type. Used by non-double-elim
   phase types; double-elim has its own multi-bracket generator."
  [phase-type standings all-matches qualified-players]
  (case phase-type
    "swiss"              (rules/swiss-pair standings all-matches)
    "round-robin"        (rules/swiss-pair standings all-matches) ;; same pairing logic, different semantics
    "single-elimination" (rules/generate-elimination-bracket qualified-players)
    ;; Default: Swiss-style pairing
    (rules/swiss-pair standings all-matches)))

(defn- create-round-matches
  "Creates matches for a round from pairings and returns the match entities.
   Bye matches (nil player-two-sub) are auto-completed with player-one as winner."
  [dependencies tournament-eid phase-index round-index bracket-type format pairings]
  (mapv (fn [pairing]
          (let [match (db/create-match
                       (:connection dependencies)
                       tournament-eid
                       (assoc pairing
                              :phase-index phase-index
                              :round-index round-index
                              :bracket-type bracket-type
                              :format format))]
            (if (nil? (:player-two-sub pairing))
              (do (db/update-match-result (:connection dependencies) (:eid match) (:player-one-sub pairing))
                  (assoc match :status "complete" :winner-sub (:player-one-sub pairing)))
              match)))
        pairings))

(defn- has-pending-matches?
  "Returns true if any matches in the given round are still pending."
  [phase-matches round-index]
  (some #(= "pending" (:status %))
        (filter #(= round-index (:round-index %)) phase-matches)))

(defn- round-format
  "Resolves the bo-N format for a round. Falls back to the first round's
   format, then to bo1.

   Note: double-elim phases have more logical rounds than the organizer
   configures under :rounds — losers rounds past index (count :rounds)
   and the grand final all miss the configured list and pick up the
   first-round format. Treat that as the intended default until a
   per-bracket format override is wanted."
  [phase round-index]
  (or (get-in phase [:rounds round-index :format])
      (get-in phase [:rounds 0 :format])
      1))

(defn- generate-double-elim-next-rounds
  "Generates every round in a double-elimination phase whose dependencies
   are satisfied and that has not already been generated. Returns the
   vector of created match entities (possibly empty).

   Plan-then-execute: on each iteration, ask rules for the next ready
   round against the current immutable snapshot of phase-matches. If one
   comes back, write its matches and recur with the extended snapshot.
   Stop when nothing is ready. Bye-only rounds auto-complete on write,
   which may unblock a subsequent round in the same call."
  [dependencies tournament-eid phase-index phase qualified]
  (let [wb-rounds (rules/winners-bracket-round-count (count qualified))
        lb-rounds (rules/losers-bracket-round-count (count qualified))
        initial   (filterv #(= phase-index (:phase-index %))
                           (get-matches-for-tournament dependencies tournament-eid))]
    (loop [phase-matches initial
           created       []]
      (let [{:keys [bracket round pairings]}
            (rules/next-ready-double-elim-round phase-matches qualified wb-rounds lb-rounds)]
        (cond
          (nil? bracket)
          created

          (empty? pairings)
          created

          :else
          (let [new-matches (create-round-matches
                             dependencies tournament-eid phase-index round bracket
                             (round-format phase round) pairings)]
            (recur (into phase-matches new-matches)
                   (into created new-matches))))))))

(defn generate-next-round
  "Generates the next round of matches for the tournament. If the current
   phase has no more rounds configured, automatically advances to the next
   phase and generates its first round. Dispatches pairing strategy based
   on the phase's phase-type."
  [dependencies tournament-eid user-sub]
  (let [tournament (get-tournament-by-eid dependencies tournament-eid)]
    (cond
      (nil? tournament)
      {:type :tournament/phase-error :message "Tournament not found."}

      (not= user-sub (:created-by-sub tournament))
      {:type :tournament/phase-error :message "Only the tournament organizer can generate rounds."}

      :else
      (let [state           (get-tournament-state dependencies tournament-eid)
            phase-index     (:current-phase state)
            phase           (get-in state [:phases phase-index])
            all-matches     (get-matches-for-tournament dependencies tournament-eid)
            phase-matches   (filter #(= phase-index (:phase-index %)) all-matches)
            qualifier-count (or (:qualifier-count state) (count (:standings state)))
            qualified       (->> (:standings state)
                                 (sort-by :points >)
                                 (take qualifier-count)
                                 (mapv :player-sub))]
        (cond
          (nil? phase)
          {:type :tournament/phase-error :message "No active phase configured."}

          ;; Double-elim: generate every dependency-satisfied round across WB, LB, GF.
          (= "double-elimination" (:phase-type phase))
          (let [created (generate-double-elim-next-rounds
                         dependencies tournament-eid phase-index phase qualified)]
            (if (empty? created)
              (let [next-phase-index (inc phase-index)
                    next-phase       (get-in state [:phases next-phase-index])]
                (if (nil? next-phase)
                  {:type :tournament/phase-error :message "All phases complete. No more rounds to generate."}
                  (let [format    (round-format next-phase 0)
                        pairings  (generate-pairings (:phase-type next-phase) (:standings state) all-matches qualified)
                        matches   (create-round-matches dependencies tournament-eid next-phase-index 0 "winners" format pairings)
                        new-state (assoc state :current-phase next-phase-index)]
                    (set-tournament-state dependencies tournament-eid new-state)
                    {:type           :tournament/round-generated
                     :round          0
                     :phase          next-phase-index
                     :phase-advanced true
                     :matches        matches})))
              {:type    :tournament/round-generated
               :phase   phase-index
               :matches created}))

          :else
          (let [next-round   (if (empty? phase-matches)
                               0
                               (inc (apply max (map :round-index phase-matches))))
                rounds       (:rounds phase)
                round-config (get rounds next-round)]
            (cond
              ;; Previous round still has pending matches
              (and (pos? next-round) (has-pending-matches? phase-matches (dec next-round)))
              {:type :tournament/phase-error :message "Previous round has pending matches."}

              ;; Current phase exhausted — auto-advance to next phase
              (nil? round-config)
              (let [next-phase-index (inc phase-index)
                    next-phase       (get-in state [:phases next-phase-index])]
                (if (nil? next-phase)
                  {:type :tournament/phase-error :message "All phases complete. No more rounds to generate."}
                  (let [format    (round-format next-phase 0)
                        pairings  (generate-pairings (:phase-type next-phase) (:standings state) all-matches qualified)
                        matches   (create-round-matches dependencies tournament-eid next-phase-index 0 "winners" format pairings)
                        new-state (assoc state :current-phase next-phase-index)]
                    (set-tournament-state dependencies tournament-eid new-state)
                    {:type           :tournament/round-generated
                     :round          0
                     :phase          next-phase-index
                     :phase-advanced true
                     :matches        matches})))

              ;; Generate next round in current phase
              :else
              (let [format    (or (:format round-config) 1)
                    pairings  (generate-pairings (:phase-type phase) (:standings state) all-matches qualified)
                    matches   (create-round-matches dependencies tournament-eid phase-index next-round "winners" format pairings)
                    new-state (update-in state [:phases phase-index :rounds next-round]
                                         assoc :match-eids (mapv :eid matches)
                                         :status "active")]
                (set-tournament-state dependencies tournament-eid new-state)
                {:type    :tournament/round-generated
                 :round   next-round
                 :phase   phase-index
                 :matches matches}))))))))
