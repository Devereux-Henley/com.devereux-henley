(ns com.devereux-henley.rts-domain.handlers.tournament
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.handlers.season :as handlers.season]
   [com.devereux-henley.rts-domain.rules.tournament :as rules]
   [com.devereux-henley.rts-domain.time :as time])
  (:import
   [java.time Duration Instant]
   [java.util Date]))

(defn- tag-tournament
  [tournament]
  (some-> tournament (assoc :type :tournament/tournament)))

(defn- tag-match
  [match]
  (some-> match (assoc :type :tournament/match)))

(defn get-tournament-by-eid
  "Fetches a tournament by eid and attaches :type :tournament/tournament."
  [dependencies eid]
  (tag-tournament (db/tournament-by-eid (:datalog-connection dependencies) eid)))

(defn get-tournaments-for-game
  "Returns all tournaments for a game, each tagged with :type :tournament/tournament."
  [dependencies game-eid]
  (mapv tag-tournament
        (db/tournaments-for-game (:datalog-connection dependencies) game-eid)))

(defn get-tournaments
  "Returns every tournament in the system, each tagged with :type :tournament/tournament."
  [dependencies]
  (mapv tag-tournament
        (db/tournaments (:datalog-connection dependencies))))

(defn- derive-standings
  "Computes standings from the entry set folded with completed match
   results (`rules/recalculate-standings`). Empty during registration."
  [dependencies tournament-eid status]
  (if (= "registration" status)
    []
    (let [conn      (:datalog-connection dependencies)
          base      (mapv (fn [e] {:player-sub (:player-sub e)
                                   :wins       0               :losses 0 :draws 0 :points 0})
                          (db/entries-for-tournament conn tournament-eid))
          completed (filterv #(= "complete" (:status %))
                             (db/matches-for-tournament conn tournament-eid))]
      (rules/recalculate-standings base completed))))

(defn get-tournament-state
  "Builds the tournament state map the rules engine and templates consume
   from its entities (status, registration window, phases, current phase,
   derived standings, qualifier count). Returns a default initial state
   when the tournament is absent."
  [dependencies tournament-eid]
  (let [tournament (db/tournament-by-eid (:datalog-connection dependencies) tournament-eid)]
    (if-not tournament
      {:status          "registration"
       :registration    {:opens-at nil :closes-at nil :closed-early false}
       :phases          []
       :current-phase   nil
       :standings       []
       :qualifier-count nil}
      (let [status (:status tournament)]
        {:status          status
         :registration    {:opens-at     (:registration-opens-at tournament)
                           :closes-at    (:registration-closes-at tournament)
                           :timezone     (:timezone tournament)
                           :closed-early (:registration-closed-early tournament)}
         :phases          (db/phases-for-tournament (:datalog-connection dependencies) tournament-eid)
         :current-phase   (:current-phase-index tournament)
         :standings       (derive-standings dependencies tournament-eid status)
         :qualifier-count (:qualifier-count tournament)}))))

(defn create-tournament
  "Creates a new tournament in registration status. When :season-eid is
   supplied, derives :league-eid from the season server-side and rejects
   any caller-supplied :league-eid that disagrees. Both keys are optional —
   tournaments may stand alone."
  [dependencies create-specification]
  (let [conn       (:datalog-connection dependencies)
        season     (when-let [seid (:season-eid create-specification)]
                     (db/season-by-eid conn seid))
        derived-le (cond
                     season                             (:league-eid season)
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
      (let [created-at (Instant/now)
            tz         (:timezone create-specification)
            opens-at   (time/to-utc-instant (:registration-opens-at create-specification) tz)
            closes-at  (time/to-utc-instant (:registration-closes-at create-specification) tz)
            spec       (-> create-specification
                           (assoc :created-at created-at
                                  :updated-at created-at
                                  :registration-opens-at opens-at
                                  :registration-closes-at closes-at)
                           (cond-> derived-le (assoc :league-eid derived-le))
                           (cond-> (not derived-le) (dissoc :league-eid)))
            tournament (db/create-tournament! conn spec)]
        (tag-tournament tournament)))))

;; ─── Registration ────────────────────────────────────────────────────────────

(defn- ->instant
  "Coerce a stored registration timestamp (`java.util.Date`) — or a passed
   `Instant`/nil — to an `Instant` for window comparisons."
  ^Instant [x]
  (cond
    (nil? x)              nil
    (instance? Instant x) x
    (instance? Date x)    (.toInstant ^Date x)))

(defn is-registration-open?
  "Returns true if the tournament state allows new registrations.
   Checks status, time window, and closed-early flag."
  [state now]
  (and (= "registration" (:status state))
       (not (get-in state [:registration :closed-early]))
       (let [opens-at  (->instant (get-in state [:registration :opens-at]))
             closes-at (->instant (get-in state [:registration :closes-at]))]
         (and (or (nil? opens-at)  (not (.isBefore now opens-at)))
              (or (nil? closes-at) (.isBefore now closes-at))))))

(defn create-entry
  "Creates a tournament entry for a player. Returns the entry or an error map."
  [dependencies tournament-eid player-sub]
  (let [state (get-tournament-state dependencies tournament-eid)
        now   (Instant/now)]
    (if-not (is-registration-open? state now)
      {:type :tournament/entry-error :message "Registration is not open."}
      (if-let [entry (db/create-entry! (:datalog-connection dependencies) tournament-eid player-sub)]
        (assoc entry :type :tournament/entry)
        {:type :tournament/entry-error :message "Already entered in this tournament."}))))

(defn delete-entry
  "Removes a player's entry from a tournament. Returns a success or error map."
  [dependencies tournament-eid player-sub]
  (let [state (get-tournament-state dependencies tournament-eid)]
    (if-not (= "registration" (:status state))
      {:type :tournament/entry-error :message "Cannot withdraw outside of registration period."}
      (do
        (db/delete-entry! (:datalog-connection dependencies) tournament-eid player-sub)
        {:type :tournament/entry-deleted :message "Entry removed from tournament."}))))

(defn get-entries
  "Returns all active entries for a tournament."
  [dependencies tournament-eid]
  (mapv (fn [e] (assoc e :type :tournament/entry))
        (db/entries-for-tournament (:datalog-connection dependencies) tournament-eid)))

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
  "Transitions a tournament from registration to active, pointing
   `current-phase-index` at the first phase when one is configured. Only
   the organizer can start. Returns `:tournament/started` on success,
   `:tournament/start-error` on failure."
  [dependencies tournament-eid user-sub]
  (or (organizer-error dependencies tournament-eid user-sub :tournament/start-error)
      (let [state (get-tournament-state dependencies tournament-eid)]
        (if (not= "registration" (:status state))
          {:type    :tournament/start-error
           :message (str "Cannot start: tournament is '" (:status state) "', not 'registration'.")}
          (do
            (db/update-tournament! (:datalog-connection dependencies) tournament-eid
                                   {:status              "active"
                                    :current-phase-index (when (seq (:phases state)) 0)})
            {:type :tournament/started :state (get-tournament-state dependencies tournament-eid)})))))

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
          (do
            (db/update-tournament! (:datalog-connection dependencies) tournament-eid {:status "complete"})
            {:type :tournament/completed :state (get-tournament-state dependencies tournament-eid)})))))

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
          (do
            (db/update-tournament! (:datalog-connection dependencies) tournament-eid {:status "cancelled"})
            {:type :tournament/cancelled :state (get-tournament-state dependencies tournament-eid)})))))

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
          (do
            (db/update-tournament! (:datalog-connection dependencies) tournament-eid
                                   {:registration-closed-early true})
            {:type :tournament/registration-closed :state (get-tournament-state dependencies tournament-eid)})))))

;; ─── Matches ─────────────────────────────────────────────────────────────────

(defn create-match
  "Creates a match within a tournament. Tournament must be active."
  [dependencies tournament-eid match-spec]
  (let [state (get-tournament-state dependencies tournament-eid)]
    (if (not= "active" (:status state))
      {:type :tournament/match-error :message "Tournament must be active to create matches."}
      (tag-match (db/create-match! (:datalog-connection dependencies) tournament-eid match-spec)))))

(defn get-match-by-eid
  "Fetches a match by eid."
  [dependencies match-eid]
  (tag-match (db/match-by-eid (:datalog-connection dependencies) match-eid)))

(defn get-matches
  "Returns every match in the system, each tagged with :type :tournament/match."
  [dependencies]
  (mapv tag-match (db/matches (:datalog-connection dependencies))))

(defn get-matches-for-tournament
  "Returns all matches for a tournament."
  [dependencies tournament-eid]
  (mapv tag-match
        (db/matches-for-tournament (:datalog-connection dependencies) tournament-eid)))

(defn get-matches-for-round
  "Returns matches for a specific phase and round."
  [dependencies tournament-eid phase-index round-index]
  (mapv tag-match
        (db/matches-for-round (:datalog-connection dependencies) tournament-eid phase-index round-index)))

;; ─── Series check-in ─────────────────────────────────────────────────────────
;;
;; A series is checked in once, covering every game of the Bo-N. The organizer
;; opens a window; each participant confirms within it. Both sides confirmed is
;; the signal the series lobby reveals (consumed downstream by the lobby flow).

(def check-in-window-minutes
  "Length of the series check-in window, in minutes, from the moment the
   organizer opens it."
  15)

(defn check-in-state
  "Derives the series check-in state for a match at `now` (an `Instant`):
   the window bounds, whether the window is currently open, and each side's
   checked-in flag/timestamp. The window is open only while the match is still
   pending, so a match that completes — or whose window lapses — reports
   `:window-open?` false. `:both-checked?` is the signal the series lobby
   reveals its code."
  [match now]
  (let [opens-at       (:check-in-opens-at match)
        closes-at      (:check-in-closes-at match)
        p1-at          (:player-one-checked-at match)
        p2-at          (:player-two-checked-at match)
        opened?        (some? opens-at)
        closes-at-inst (->instant closes-at)]
    {:opens-at              opens-at
     :closes-at             closes-at
     :opened?               opened?
     :window-open?          (boolean (and opened?
                                          (= "pending" (:status match))
                                          (or (nil? closes-at-inst) (.isBefore now closes-at-inst))))
     :player-one-checked?   (some? p1-at)
     :player-two-checked?   (some? p2-at)
     :player-one-checked-at p1-at
     :player-two-checked-at p2-at
     :both-checked?         (and (some? p1-at) (some? p2-at))}))

(defn open-check-in
  "Opens the series check-in window on a match. Only the tournament organizer
   may open it; the match must still be pending and have two players (a bye has
   no opponent to coordinate with). Sets a window that closes
   `check-in-window-minutes` after now. Re-opening advances the close time while
   preserving the original open time and any prior check-ins. Returns
   `:tournament/check-in-opened` with the updated match and derived state, or
   `:tournament/check-in-error`."
  [dependencies match-eid user-sub]
  (let [conn  (:datalog-connection dependencies)
        match (db/match-by-eid conn match-eid)]
    (if (nil? match)
      {:type :tournament/check-in-error :message "Match not found."}
      (or (organizer-error dependencies (:tournament-eid match) user-sub :tournament/check-in-error)
          (cond
            (= "complete" (:status match))
            {:type :tournament/check-in-error :message "Cannot open check-in for a completed match."}

            (nil? (:player-two-sub match))
            {:type :tournament/check-in-error :message "This match has no opponent; check-in does not apply to a bye."}

            :else
            (let [now       (Instant/now)
                  opens-at  (or (->instant (:check-in-opens-at match)) now)
                  closes-at (.plus now (Duration/ofMinutes check-in-window-minutes))
                  updated   (db/open-match-check-in! conn match-eid
                                                     {:opens-at opens-at :closes-at closes-at})]
              {:type     :tournament/check-in-opened
               :match    (tag-match updated)
               :check-in (check-in-state updated now)}))))))

(defn check-in-player
  "Records a player's series check-in. The caller must be one of the match's
   participants and the match still pending. Idempotent: a player already
   checked in gets success without a second write, even after the window has
   closed. A first check-in requires the window to be open. Returns
   `:tournament/checked-in` with the updated match and derived state, or
   `:tournament/check-in-error`."
  [dependencies match-eid user-sub]
  (let [conn  (:datalog-connection dependencies)
        match (db/match-by-eid conn match-eid)]
    (cond
      (nil? match)
      {:type :tournament/check-in-error :message "Match not found."}

      (not (contains? #{(:player-one-sub match) (:player-two-sub match)} user-sub))
      {:type :tournament/check-in-error :message "Only match participants can check in."}

      (= "complete" (:status match))
      {:type :tournament/check-in-error :message "This match is already complete."}

      :else
      (let [now      (Instant/now)
            state    (check-in-state match now)
            side     (if (= user-sub (:player-one-sub match)) :player-one :player-two)
            already? (case side
                       :player-one (:player-one-checked? state)
                       :player-two (:player-two-checked? state))]
        (cond
          ;; Idempotent re-check — succeed without a second write, regardless of
          ;; whether the window has since closed (a double-submit shouldn't 422).
          already?
          {:type  :tournament/checked-in :side     side
           :match (tag-match match)      :check-in state}

          (not (:window-open? state))
          {:type :tournament/check-in-error :message "The check-in window is not open."}

          :else
          (let [updated (db/record-match-check-in! conn match-eid side now)]
            {:type     :tournament/checked-in
             :side     side
             :match    (tag-match updated)
             :check-in (check-in-state updated now)}))))))

(defn get-check-in-state
  "Reads the derived series check-in state for a match — for the player console
   view-models — or nil when the match is absent."
  [dependencies match-eid]
  (when-let [match (db/match-by-eid (:datalog-connection dependencies) match-eid)]
    (check-in-state match (Instant/now))))

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
  "Recomputes the (derived) standings after a match completes and, when
   phases are configured and every match across all phases is done with no
   more rounds remaining, auto-completes the tournament. Standings are
   derived from entities, so the only write here is the completion flip."
  [dependencies tournament-eid]
  (let [state         (get-tournament-state dependencies tournament-eid)
        all-matches   (get-matches-for-tournament dependencies tournament-eid)
        new-standings (:standings state)
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
                              (and all-done (nil? rounds-left))))))]
    (when complete?
      (db/update-tournament! (:datalog-connection dependencies) tournament-eid {:status "complete"}))
    {:standings           new-standings
     :tournament-complete (boolean complete?)}))

(defn update-match-result
  "Records a match result and recalculates standings from entities."
  [dependencies match-eid winner-sub]
  (let [match (db/match-by-eid (:datalog-connection dependencies) match-eid)]
    (cond
      (nil? match)
      {:type :tournament/match-error :message "Match not found."}

      (not= "pending" (:status match))
      {:type :tournament/match-error :message "Match is not pending."}

      :else
      (do
        (db/update-match-result! (:datalog-connection dependencies) match-eid winner-sub)
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
  (let [match (db/match-by-eid (:datalog-connection dependencies) match-eid)]
    (cond
      (nil? match)
      {:type :tournament/match-error :message "Match not found."}

      (= "complete" (:status match))
      {:type :tournament/match-error :message "Match is already complete."}

      :else
      (let [conn           (:datalog-connection dependencies)
            existing-games (db/games-for-match conn match-eid)
            game-index     (count existing-games)
            _              (db/create-game! conn match-eid game-index winner-sub)
            all-games      (conj (vec existing-games) {:winner-sub winner-sub})
            match-winner   (rules/check-match-complete all-games (:format match))]
        (if match-winner
          ;; Match complete — update match, recalculate standings, check tournament completion
          (do
            (db/update-match-result! conn match-eid match-winner)
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
  (db/games-for-match (:datalog-connection dependencies) match-eid))

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
          (let [conn (:datalog-connection dependencies)]
            (db/set-phases! conn tournament-eid (:phases phase-config))
            (db/update-tournament! conn tournament-eid {:qualifier-count (:qualifier-count phase-config)})
            {:type  :tournament/phase-configured
             :state (get-tournament-state dependencies tournament-eid)}))))))

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
          (let [match (db/create-match!
                       (:datalog-connection dependencies)
                       tournament-eid
                       (assoc pairing
                              :phase-index phase-index
                              :round-index round-index
                              :bracket-type bracket-type
                              :format format))]
            (if (nil? (:player-two-sub pairing))
              (do (db/update-match-result! (:datalog-connection dependencies) (:eid match) (:player-one-sub pairing))
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
      (let [conn            (:datalog-connection dependencies)
            state           (get-tournament-state dependencies tournament-eid)
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
                  (let [format   (round-format next-phase 0)
                        pairings (generate-pairings (:phase-type next-phase) (:standings state) all-matches qualified)
                        matches  (create-round-matches dependencies tournament-eid next-phase-index 0 "winners" format pairings)]
                    (db/update-tournament! conn tournament-eid {:current-phase-index next-phase-index})
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
                  (let [format   (round-format next-phase 0)
                        pairings (generate-pairings (:phase-type next-phase) (:standings state) all-matches qualified)
                        matches  (create-round-matches dependencies tournament-eid next-phase-index 0 "winners" format pairings)]
                    (db/update-tournament! conn tournament-eid {:current-phase-index next-phase-index})
                    {:type           :tournament/round-generated
                     :round          0
                     :phase          next-phase-index
                     :phase-advanced true
                     :matches        matches})))

              ;; Generate next round in current phase
              :else
              (let [format   (or (:format round-config) 1)
                    pairings (if (and (= "single-elimination" (:phase-type phase))
                                      (pos? next-round))
                               ;; Subsequent SE rounds advance the bracket
                               ;; from the previous round's winners; only
                               ;; round 0 seeds from standings.
                               (rules/advance-winners-bracket-round
                                (filter #(= (dec next-round) (:round-index %))
                                        phase-matches))
                               (generate-pairings (:phase-type phase) (:standings state) all-matches qualified))
                    matches  (create-round-matches dependencies tournament-eid phase-index next-round "winners" format pairings)]
                {:type    :tournament/round-generated
                 :round   next-round
                 :phase   phase-index
                 :matches matches}))))))))

;; ─── Tournament detail view-model + bracket decoration ──────────────────────

(def ^:private phase-type-labels
  {"single-elimination" "Single Elimination"
   "double-elimination" "Double Elimination"
   "swiss"              "Swiss"
   "round-robin"        "Round Robin"})

(defn- column-label
  "Bracket column heading. Branches by bracket type so DE's losers/grand-final
   rounds get appropriate labels instead of `Final` clashing with WB."
  [bracket round-index total-rounds]
  (let [distance-from-end (- (dec total-rounds) round-index)]
    (case bracket
      "grand-final" "Grand Final"
      "losers"      (case distance-from-end
                      0 "Losers Final"
                      1 "Losers Semis"
                      (str "Losers R" (inc round-index)))
      "winners"     (case distance-from-end
                      0 "Final"
                      1 "Semifinals"
                      2 "Quarterfinals"
                      (str "Round " (inc round-index)))
      ;; fallback for swiss/round-robin rounds rendered through this
      ;; same helper.
      (str "Round " (inc round-index)))))

(defn- match-label
  "Per-card label inside a bracket column."
  [bracket round-index total-rounds match-position]
  (let [distance-from-end (- (dec total-rounds) round-index)]
    (case bracket
      "grand-final" "GF"
      "losers"      (str "LB R" (inc round-index) " M" match-position)
      "winners"     (case distance-from-end
                      0 "F"
                      1 (str "SF " match-position)
                      2 (str "QF " match-position)
                      (str "R" (inc round-index) " M" match-position))
      (str "R" (inc round-index) " M" match-position))))

(defn- with-game-counts
  "Adds :p1-games-won and :p2-games-won to a match by counting :winner-sub
   in the match's games. Bracket cells display these instead of W/L."
  [dependencies match]
  (let [games (get-games-for-match dependencies (:eid match))
        wins  (frequencies (keep :winner-sub games))]
    (assoc match
           :p1-games-won (get wins (:player-one-sub match) 0)
           :p2-games-won (get wins (:player-two-sub match) 0))))

(defn- match-status-subheader
  "Returns :status-primary + :status-secondary strings for a bracket match
   based on its state. The viewer renders these as a two-line subfooter
   under the card."
  [{:keys [placeholder? p1-tbd? p2-tbd? p1-games-won p2-games-won status]}]
  (let [games-played (+ (or p1-games-won 0) (or p2-games-won 0))
        awaiting?    (or placeholder?
                         (and (= "pending" status) (or p1-tbd? p2-tbd?)))]
    (cond
      awaiting?
      {:status-primary "Awaiting bracket" :status-secondary "·"}

      (= "complete" status)
      {:status-primary "Final" :status-secondary "Settled"}

      (and (= "pending" status) (pos? games-played))
      {:status-primary (str "Game " (inc games-played)) :status-secondary "Live"}

      :else
      {:status-primary "Scheduled" :status-secondary "—"})))

(defn- decorate-bracket-round
  "Annotates one round of a bracket with the labels and per-match flags
   the template needs (Selmer can't do arithmetic comparisons, so it all
   has to be precomputed). `bracket` is `winners` / `losers` /
   `grand-final` so column/match labels branch correctly for DE."
  [bracket {:keys [round total-rounds matches] :as round-group}]
  (assoc round-group
         :column-label (column-label bracket round total-rounds)
         :matches      (mapv (fn [position match]
                               (let [p1         (:player-one-sub match)
                                     p2         (:player-two-sub match)
                                     winner     (:winner-sub match)
                                     complete?  (= "complete" (:status match))
                                     base-match (assoc match
                                                       :match-label  (match-label bracket round total-rounds position)
                                                       :p1-winner?   (and complete? (= winner p1))
                                                       :p2-winner?   (and complete? (= winner p2))
                                                       :p1-loser?    (and complete? (some? p1) (not= winner p1))
                                                       :p2-loser?    (and complete? (some? p2) (not= winner p2))
                                                       :p1-tbd?      (nil? p1)
                                                       :p2-tbd?      (nil? p2)
                                                       :p2-bye?      (and (some? p1) (nil? p2))
                                                       :placeholder? (nil? (:eid match)))]
                                 (merge base-match (match-status-subheader base-match))))
                             (iterate inc 1)
                             matches)))

(defn- relabel-winners-final
  "In a double-elimination bracket the last WB round is the 'Winners Final',
   not the tournament final — that's the grand final. Override the column
   label + per-match label so the unified bracket strip reads correctly."
  [wb-rounds]
  (if (seq wb-rounds)
    (update wb-rounds (dec (count wb-rounds))
            (fn [round]
              (-> round
                  (assoc :column-label "Winners Final")
                  (update :matches
                          (fn [matches]
                            (mapv #(assoc % :match-label "WF") matches))))))
    wb-rounds))

(defn- assign-grid
  "Decorates each round-cell with grid placement metadata: `:grid-style`
   (CSS fragment for inline style), plus `:bracket-col`, `:bracket-row`,
   `:bracket-row-span` for the connector JS to pair adjacent cells."
  ([column row cell] (assign-grid column row 1 cell))
  ([column row row-span cell]
   (assoc cell
          :grid-style       (format "grid-column: %d; grid-row: %d / span %d;"
                                    column row row-span)
          :bracket-col      column
          :bracket-row      row
          :bracket-row-span row-span)))

(defn- decorate-bracket-phase
  "Annotates a phase group with grid-positioned cells for the unified
   viewer bracket. Covers SE, DE (WB on top, LB stacked below, GF as the
   final column spanning both rows), and Swiss / round-robin (flat row).

   Exposes:
   - `:bracket-cells` — the flat list of decorated round-cells, each
     carrying `:grid-style` for explicit grid placement.
   - `:bracket-cols`  — column count for `grid-template-columns`.
   - `:bracket-rows`  — row count (1 for SE/Swiss/RR, 2 for DE)."
  [phase-group]
  (let [decorated (cond-> phase-group
                    (:winners-bracket phase-group) (update :winners-bracket #(mapv (partial decorate-bracket-round "winners") %))
                    (:losers-bracket phase-group)  (update :losers-bracket  #(mapv (partial decorate-bracket-round "losers") %))
                    (:grand-final phase-group)     (update :grand-final     #(mapv (partial decorate-bracket-round "grand-final") %))
                    (:rounds phase-group)          (update :rounds          #(mapv (partial decorate-bracket-round "rounds") %)))
        de?       (boolean (or (:losers-bracket decorated) (:grand-final decorated)))
        wb        (cond-> (:winners-bracket decorated)
                    de? relabel-winners-final)
        lb        (or (:losers-bracket decorated) [])
        gf        (or (:grand-final decorated) [])
        rounds    (or (:rounds decorated) [])
        cells     (cond
                    de?
                    (let [n     (count wb)
                          ;; 3-row grid: WB on row 1, dashed divider on
                          ;; row 2, LB on row 3. GF spans all three so
                          ;; it sits flush at the right edge.
                          wb-cs (map-indexed (fn [i r] (assign-grid (inc i) 1 r)) wb)
                          ;; Drop the per-LB column label — the WB
                          ;; column above already labels each column.
                          lb-cs (map-indexed (fn [i r]
                                               (assign-grid (+ i 2) 3 (assoc r :column-label nil)))
                                             lb)
                          gf-cs (map (fn [r] (assign-grid (inc n) 1 3 r)) gf)]
                      (vec (concat wb-cs lb-cs gf-cs)))

                    (seq rounds)
                    (vec (map-indexed (fn [i r] (assign-grid (inc i) 1 r)) rounds))

                    :else
                    (vec (map-indexed (fn [i r] (assign-grid (inc i) 1 r)) wb)))
        cols      (cond
                    de?         (inc (count wb))
                    (seq rounds) (count rounds)
                    :else        (count wb))
        rows      (if de? 3 1)]
    (assoc decorated
           :bracket-cells cells
           :bracket-cols  cols
           :bracket-rows  rows
           :bracket-divider? de?)))

(def ^:private bracket-labels
  {"winners"     "Winners"
   "losers"      "Losers"
   "grand-final" "Grand Final"})

(defn- round-buckets-for-phase
  "Flattens every bucket inside a phase-group into a flat round-list,
   tagged with the bracket it came from. Covers Swiss/RR `:rounds`, SE
   `:winners-bracket`, plus DE's `:losers-bracket` and `:grand-final`."
  [phase-group]
  (let [tag-bracket (fn [bracket rounds]
                      (mapv #(assoc % :bracket-label (bracket-labels bracket))
                            (or rounds [])))]
    (concat (tag-bracket "winners"     (or (:rounds phase-group) (:winners-bracket phase-group)))
            (tag-bracket "losers"      (:losers-bracket phase-group))
            (tag-bracket "grand-final" (:grand-final phase-group)))))

(defn- pending-matches-schedule
  "Flat, round-ordered list of pending matches across every phase and
   bracket, shaped for the viewer's Schedule list. Drops placeholder
   rows (no :eid) and complete matches. First entry is marked `:up-next?`
   so the template highlights it without peeking at forloop.first."
  [matches-by-phase]
  (let [round-buckets (mapcat round-buckets-for-phase matches-by-phase)
        rows          (->> round-buckets
                           (mapcat (fn [{:keys [round phase-type matches bracket-label]}]
                                     (let [phase-label (or (phase-type-labels phase-type)
                                                           phase-type)
                                           round-label (cond
                                                         (= "Grand Final" bracket-label) bracket-label
                                                         (= "Winners"     bracket-label) (str "Round " (inc round))
                                                         :else                           (str bracket-label " R" (inc round)))]
                                       (->> matches
                                            (filter :eid)
                                            (filter #(= "pending" (:status %)))
                                            (map #(assoc %
                                                         :round-label round-label
                                                         :phase-label phase-label))))))
                           vec)]
    (if (empty? rows)
      rows
      (assoc-in rows [0 :up-next?] true))))

(defn- current-round-label
  "Returns the column-label of the bracket cell containing the next
   pending match across all phases. Used in the hero pill so the viewer
   shows e.g. `Quarterfinals` while QF matches are still being played."
  [decorated-phases up-next-eid]
  (when up-next-eid
    (let [cells (mapcat :bracket-cells decorated-phases)
          cell  (first (filter (fn [c]
                                 (some #(= up-next-eid (:eid %)) (:matches c)))
                               cells))]
      (:column-label cell))))

(defn- decorate-standings
  "Sorts standings by points descending, then annotates each row with its
   1-based rank and a boolean for whether it falls within the qualifier cut."
  [standings qualifier-count]
  (mapv (fn [rank row]
          (assoc row
                 :rank      rank
                 :advanced? (and qualifier-count (<= rank qualifier-count))))
        (iterate inc 1)
        (sort-by #(- (or (:points %) 0)) standings)))

(defn tournament-view-model
  "Builds the tournament detail view-model: the tournament entity under
   `:data`, its derived state, entries, per-phase bracket grids, pending-match
   schedule, phase labels, parent league/season, and the viewer-dependent
   flags (`:has-entry`, `:registration-open`, `:is-organizer`). Returns a
   `:missing/resource` marker when the tournament doesn't exist."
  [dependencies eid viewer-sub]
  (if-let [tournament (get-tournament-by-eid dependencies eid)]
    (let [conn                 (:datalog-connection dependencies)
          state                (get-tournament-state dependencies eid)
          entries              (get-entries dependencies eid)
          raw-matches          (mapv (partial with-game-counts dependencies)
                                     (get-matches-for-tournament dependencies eid))
          phases               (:phases state)
          qualifier-count      (or (:qualifier-count state) (count (:standings state)))
          matches-by-phase     (rules/group-matches-by-phase raw-matches phases qualifier-count)
          decorated-phases     (mapv decorate-bracket-phase matches-by-phase)
          current-phase-config (get phases (:current-phase state))
          phase-count          (count phases)
          schedule             (pending-matches-schedule matches-by-phase)]
      {:data                tournament
       :tournament-state    (update state :standings decorate-standings (:qualifier-count state))
       :entries             entries
       :matches-by-phase    decorated-phases
       :schedule            schedule
       :current-phase-label (or (phase-type-labels (:phase-type current-phase-config))
                                (:phase-type current-phase-config))
       :current-round-label (current-round-label decorated-phases (:eid (first schedule)))
       :phase-count         phase-count
       :single-phase?       (= 1 phase-count)
       :league              (when (:league-eid tournament)
                              (db/league-by-eid conn (:league-eid tournament)))
       :season              (when (:season-eid tournament)
                              (handlers.season/get-season-by-eid dependencies (:season-eid tournament)))
       :has-entry           (boolean (some #(= viewer-sub (:player-sub %)) entries))
       :registration-open   (is-registration-open? state (Instant/now))
       :is-organizer        (= viewer-sub (:created-by-sub tournament))})
    {:type :missing/resource :name "tournament" :id eid}))

(defn- current-round-progress
  "Summarizes how far the latest round of the active phase has reported:
   the round index, how many matches it holds, how many have a recorded
   result, and whether every match has reported. The round-complete? flag
   is the gate the organizer console uses to enable phase advancement."
  [matches current-phase]
  (let [phase-matches (filter #(= current-phase (:phase-index %)) matches)]
    (if (empty? phase-matches)
      {:round-index nil :round-total 0 :round-reported 0 :round-complete? false}
      (let [current-round (apply max (map :round-index phase-matches))
            round-matches (filter #(= current-round (:round-index %)) phase-matches)
            total         (count round-matches)
            reported      (count (filter #(= "complete" (:status %)) round-matches))]
        {:round-index     current-round
         :round-total     total
         :round-reported  reported
         :round-complete? (= total reported)}))))

(defn- tournament-progression
  "Derives what the single 'progress tournament' shelf action does next,
   given the state, the current round progress, and whether the tournament
   is active. `generate-next-round` is one operation that seeds the next
   round and auto-advances the phase when the phase's rounds run out, so the
   shelf collapses to one control whose meaning depends on position:

     :progress-state   \"round\"    — more rounds remain in the current phase
                       \"phase\"    — last round of the phase, but more phases follow
                       \"done\"     — final round of the final phase; nothing to generate
                       \"inactive\" — tournament isn't active
     :can-progress?    true when the action can run now: active, not done, and
                       either no round exists yet (seed the first) or the
                       current round has fully reported.
     :next-phase-number 1-based number of the phase a `phase` advance moves into."
  [state progress active?]
  (let [current-phase (:current-phase state)
        phases        (:phases state)
        phase-config  (when current-phase (get phases current-phase))
        total-rounds  (count (:rounds phase-config))
        round-index   (:round-index progress)
        more-rounds?  (if (nil? round-index)
                        (pos? total-rounds)
                        (< (inc round-index) total-rounds))
        more-phases?  (boolean (when current-phase (get phases (inc current-phase))))
        state-name    (cond
                        (not active?) "inactive"
                        more-rounds?  "round"
                        more-phases?  "phase"
                        :else         "done")
        round-gated?  (and (pos? (:round-total progress))
                           (not (:round-complete? progress)))]
    {:progress-state    state-name
     :done?             (= "done" state-name)
     :more-phases?      more-phases?
     :next-phase-number (when current-phase (+ current-phase 2))
     :can-progress?     (and active?
                             (not= "done" state-name)
                             (not round-gated?))}))

(defn organizer-view-model
  "Builds the organizer-console view-model: everything `tournament-view-model`
   produces plus the current round's reporting progress and the single
   `tournament-progression` summary the Quick Actions shelf reads. Returns a
   `:missing/resource` marker when the tournament doesn't exist."
  [dependencies eid viewer-sub]
  (let [model (tournament-view-model dependencies eid viewer-sub)]
    (if (= :missing/resource (:type model))
      model
      (let [state    (:tournament-state model)
            active?  (= "active" (:status state))
            progress (current-round-progress (get-matches-for-tournament dependencies eid)
                                             (:current-phase state))]
        (merge model
               progress
               {:active? active?}
               (tournament-progression state progress active?))))))
