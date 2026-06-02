(ns com.devereux-henley.rts-data-access.query.datalog.tournament
  "Datalevin reads and mutations for the tournament domain — tournaments,
  phases/rounds, entries, matches, and per-match games.

  Reads return flat unqualified-key maps: enum keywords flatten to strings
  (`:match/status :complete` → `\"complete\"`), ref sub-maps become `*-eid`
  fields, and instants are `java.util.Date`. Status, the registration
  window, `current-phase-index`, and `qualifier-count` live on the
  tournament entity; standings are derived (not read) by the handler from
  entries + completed matches."
  (:require
   [com.devereux-henley.datalog.contract :as dl])
  (:import
   [java.time Instant]
   [java.util Date]))

;;; ─── Coercions ─────────────────────────────────────────────────────────────

(defn- ->date
  "Coerce an `Instant`/`Date`/nil to the `java.util.Date` Datalevin stores
  for `:db.type/instant`. Callers stamp times as `Instant`; the boundary
  converts."
  ^Date [x]
  (cond
    (nil? x)              nil
    (instance? Date x)    x
    (instance? Instant x) (Date/from ^Instant x)))

(defn- kw->str
  "Enum keyword → the string the rules/handlers compare against."
  [k]
  (some-> k name))

;;; ─── Pull patterns ─────────────────────────────────────────────────────────

(def ^:private tournament-pattern
  [:tournament/eid :tournament/name :tournament/description
   :tournament/created-by-sub :tournament/version
   :tournament/created-at :tournament/updated-at
   :tournament/status :tournament/timezone
   :tournament/registration-opens-at :tournament/registration-closes-at
   :tournament/registration-closed-early
   :tournament/current-phase-index :tournament/qualifier-count
   {:tournament/game [:game/eid]}
   {:tournament/league [:league/eid]}
   {:tournament/season [:season/eid]}])

(def ^:private phases-pattern
  [{:tournament/phases [:tournament-phase/ordinal :tournament-phase/phase-type
                        {:tournament-phase/rounds [:tournament-round/round-index
                                                   :tournament-round/format]}]}])

(def ^:private entry-pattern
  [:tournament-entry/eid :tournament-entry/player-sub :tournament-entry/created-at
   {:tournament-entry/tournament [:tournament/eid]}])

(def ^:private match-pattern
  [:match/eid :match/phase-index :match/round-index :match/bracket-type
   :match/player-one-sub :match/player-two-sub :match/winner-sub
   :match/status :match/format
   :match/check-in-opens-at :match/check-in-closes-at
   :match/player-one-checked-at :match/player-two-checked-at
   :match/lobby-code
   :match/created-at :match/updated-at
   {:match/tournament [:tournament/eid]}])

(def ^:private match-game-pattern
  [:match-game/eid :match-game/game-index :match-game/winner-sub
   :match-game/uploader-local-alliance-index :match-game/created-at
   {:match-game/replay [:replay/eid]}
   {:match-game/player-one-draft [:draft/eid]}
   {:match-game/player-two-draft [:draft/eid]}
   {:match/_games [:match/eid]}])

;;; ─── Result builders ──────────────────────────────────────────────────────

(defn- ->tournament
  [m]
  (when m
    (cond-> {:eid                       (:tournament/eid m)
             :name                      (:tournament/name m)
             :description               (:tournament/description m)
             :created-by-sub            (:tournament/created-by-sub m)
             :version                   (:tournament/version m)
             :created-at                (:tournament/created-at m)
             :updated-at                (:tournament/updated-at m)
             :status                    (kw->str (:tournament/status m))
             :timezone                  (:tournament/timezone m)
             :registration-opens-at     (:tournament/registration-opens-at m)
             :registration-closes-at    (:tournament/registration-closes-at m)
             :registration-closed-early (boolean (:tournament/registration-closed-early m))
             :current-phase-index       (:tournament/current-phase-index m)
             :qualifier-count           (:tournament/qualifier-count m)
             :game-eid                  (some-> m :tournament/game :game/eid)}
      (some-> m :tournament/league :league/eid) (assoc :league-eid (-> m :tournament/league :league/eid))
      (some-> m :tournament/season :season/eid) (assoc :season-eid (-> m :tournament/season :season/eid)))))

(defn- ->phases
  "Reconstruct the `[{:phase-type … :rounds [{:round-index … :format …}]}]`
  shape the rules/handlers consume, sorted by phase ordinal then round
  index so the JSON-array order is restored from the unordered datalog
  sets."
  [pulled]
  (->> (:tournament/phases pulled)
       (sort-by :tournament-phase/ordinal)
       (mapv (fn [phase]
               {:phase-type (kw->str (:tournament-phase/phase-type phase))
                :rounds     (->> (:tournament-phase/rounds phase)
                                 (sort-by :tournament-round/round-index)
                                 (mapv (fn [r]
                                         {:round-index (:tournament-round/round-index r)
                                          :format      (:tournament-round/format r)})))}))))

(defn- ->entry
  [m]
  (when m
    {:eid            (:tournament-entry/eid m)
     :tournament-eid (some-> m :tournament-entry/tournament :tournament/eid)
     :player-sub     (:tournament-entry/player-sub m)
     :created-at     (:tournament-entry/created-at m)}))

(defn- ->match
  [m]
  (when m
    {:eid                   (:match/eid m)
     :tournament-eid        (some-> m :match/tournament :tournament/eid)
     :phase-index           (:match/phase-index m)
     :round-index           (:match/round-index m)
     :bracket-type          (kw->str (:match/bracket-type m))
     :player-one-sub        (:match/player-one-sub m)
     :player-two-sub        (:match/player-two-sub m)
     :winner-sub            (:match/winner-sub m)
     :status                (kw->str (:match/status m))
     :format                (:match/format m)
     :check-in-opens-at     (:match/check-in-opens-at m)
     :check-in-closes-at    (:match/check-in-closes-at m)
     :player-one-checked-at (:match/player-one-checked-at m)
     :player-two-checked-at (:match/player-two-checked-at m)
     :lobby-code            (:match/lobby-code m)
     :created-at            (:match/created-at m)
     :updated-at            (:match/updated-at m)}))

(defn- ->game
  [m]
  (when m
    {:eid                           (:match-game/eid m)
     :match-eid                     (some-> m :match/_games first :match/eid)
     :game-index                    (:match-game/game-index m)
     :winner-sub                    (:match-game/winner-sub m)
     :uploader-local-alliance-index (:match-game/uploader-local-alliance-index m)
     :replay-eid                    (some-> m :match-game/replay :replay/eid)
     :player-one-draft-eid          (some-> m :match-game/player-one-draft :draft/eid)
     :player-two-draft-eid          (some-> m :match-game/player-two-draft :draft/eid)
     :created-at                    (:match-game/created-at m)}))

;;; ─── Tournament reads ──────────────────────────────────────────────────────

(defn tournament-by-eid
  "Fetch a tournament by eid. Returns nil when not found."
  [conn eid]
  (->tournament (dl/pull (dl/db conn) tournament-pattern (dl/lookup-ref :tournament/eid eid))))

(defn tournaments-for-game
  "All tournaments scoped to a game, newest first (created-at desc)."
  [conn game-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?t pattern) ...]
                 :in $ pattern ?game-eid
                 :where
                 [?t :tournament/game ?g]
                 [?g :game/eid ?game-eid]]
               db tournament-pattern game-eid)
         (mapv ->tournament)
         (sort-by (juxt (comp - (fnil #(.getTime ^Date %) (Date. 0)) :created-at) :eid))
         vec)))

(defn tournaments
  "Every tournament in the system."
  [conn]
  (->> (dl/q '[:find [(pull ?t pattern) ...]
               :in $ pattern
               :where [?t :tournament/eid]]
             (dl/db conn) tournament-pattern)
       (mapv ->tournament)
       vec))

(defn phases-for-tournament
  "The `[{:phase-type … :rounds […]}]` phase configuration for a tournament,
  reconstructed from its phase/round entities."
  [conn eid]
  (->phases (dl/pull (dl/db conn) phases-pattern (dl/lookup-ref :tournament/eid eid))))

;;; ─── Entry reads ───────────────────────────────────────────────────────────

(defn entries-for-tournament
  "All entries for a tournament, oldest first (created-at asc)."
  [conn eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?e pattern) ...]
                 :in $ pattern ?tour-eid
                 :where
                 [?e :tournament-entry/tournament ?t]
                 [?t :tournament/eid ?tour-eid]]
               db entry-pattern eid)
         (mapv ->entry)
         (sort-by (juxt (comp (fnil #(.getTime ^Date %) (Date. 0)) :created-at) :eid))
         vec)))

(defn entry-by-tournament-and-player
  "The entry a player holds in a tournament, or nil."
  [conn eid player-sub]
  (let [db (dl/db conn)]
    (some-> (dl/q '[:find (pull ?e pattern) .
                    :in $ pattern ?tour-eid ?player-sub
                    :where
                    [?e :tournament-entry/tournament ?t]
                    [?t :tournament/eid ?tour-eid]
                    [?e :tournament-entry/player-sub ?player-sub]]
                  db entry-pattern eid player-sub)
            ->entry)))

;;; ─── Match reads ───────────────────────────────────────────────────────────

(defn match-by-eid
  "Fetch a match by eid. Returns nil when not found."
  [conn eid]
  (->match (dl/pull (dl/db conn) match-pattern (dl/lookup-ref :match/eid eid))))

(defn matches-for-tournament
  "All matches in a tournament, ordered by (phase-index, round-index)."
  [conn eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?m pattern) ...]
                 :in $ pattern ?tour-eid
                 :where
                 [?m :match/tournament ?t]
                 [?t :tournament/eid ?tour-eid]]
               db match-pattern eid)
         (mapv ->match)
         (sort-by (juxt :phase-index :round-index :eid))
         vec)))

(defn matches
  "Every match in the system."
  [conn]
  (->> (dl/q '[:find [(pull ?m pattern) ...]
               :in $ pattern
               :where [?m :match/eid]]
             (dl/db conn) match-pattern)
       (mapv ->match)
       vec))

(defn matches-for-round
  "Matches in a tournament's specific phase + round."
  [conn eid phase-index round-index]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?m pattern) ...]
                 :in $ pattern ?tour-eid ?phase ?round
                 :where
                 [?m :match/tournament ?t]
                 [?t :tournament/eid ?tour-eid]
                 [?m :match/phase-index ?phase]
                 [?m :match/round-index ?round]]
               db match-pattern eid phase-index round-index)
         (mapv ->match)
         (sort-by :eid)
         vec)))

(defn games-for-match
  "All games recorded for a match, ordered by game-index."
  [conn match-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?g pattern) ...]
                 :in $ pattern ?match-eid
                 :where
                 [?m :match/eid ?match-eid]
                 [?m :match/games ?g]]
               db match-game-pattern match-eid)
         (mapv ->game)
         (sort-by :game-index)
         vec)))

;;; ─── Tournament mutations ──────────────────────────────────────────────────

(defn create-tournament!
  "Transact a new tournament in `registration` status. `:league-eid` /
  `:season-eid` on the spec become refs. Registration window times are
  stamped as instants."
  [conn {:keys [eid name description game-eid league-eid season-eid created-by-sub
                version created-at updated-at registration-opens-at
                registration-closes-at timezone]}]
  (dl/transact!
   conn
   [(cond-> {:tournament/eid                       eid
             :tournament/name                      name
             :tournament/description               description
             :tournament/game                      [:game/eid game-eid]
             :tournament/created-by-sub            created-by-sub
             :tournament/version                   (or version 1)
             :tournament/created-at                (->date created-at)
             :tournament/updated-at                (->date updated-at)
             :tournament/status                    :registration
             :tournament/registration-closed-early false}
      league-eid             (assoc :tournament/league [:league/eid league-eid])
      season-eid             (assoc :tournament/season [:season/eid season-eid])
      timezone               (assoc :tournament/timezone timezone)
      registration-opens-at  (assoc :tournament/registration-opens-at (->date registration-opens-at))
      registration-closes-at (assoc :tournament/registration-closes-at (->date registration-closes-at)))])
  (tournament-by-eid conn eid))

(defn update-tournament!
  "Patch mutable tournament fields, always touching `updated-at`. Accepts
  any of `:status` (string, stored as a keyword), `:current-phase-index`,
  `:qualifier-count`, `:registration-closed-early`. Nil-valued keys are
  skipped so partial updates only write what they mean to."
  [conn eid {:keys [status current-phase-index qualifier-count registration-closed-early]}]
  (dl/transact!
   conn
   [(cond-> {:tournament/eid        eid
             :tournament/updated-at (Date.)}
      status                            (assoc :tournament/status (keyword status))
      (some? current-phase-index)       (assoc :tournament/current-phase-index current-phase-index)
      (some? qualifier-count)           (assoc :tournament/qualifier-count qualifier-count)
      (some? registration-closed-early) (assoc :tournament/registration-closed-early registration-closed-early))])
  (tournament-by-eid conn eid))

(defn set-phases!
  "Replace a tournament's phase configuration. Retracts the existing
  phase + round entities, then transacts fresh ones from `phases` — a
  vector of `{:phase-type … :rounds [{:round-index … :format …}]}`. The
  phase's position in the vector becomes its `:tournament-phase/ordinal`."
  [conn eid phases]
  (let [db          (dl/db conn)
        existing    (dl/pull db [{:tournament/phases [:tournament-phase/eid
                                                      {:tournament-phase/rounds [:tournament-round/eid]}]}]
                             (dl/lookup-ref :tournament/eid eid))
        retract-ops (mapcat (fn [phase]
                              (cons [:db/retractEntity (dl/lookup-ref :tournament-phase/eid
                                                                      (:tournament-phase/eid phase))]
                                    (map (fn [r]
                                           [:db/retractEntity (dl/lookup-ref :tournament-round/eid
                                                                             (:tournament-round/eid r))])
                                         (:tournament-phase/rounds phase))))
                            (:tournament/phases existing))
        phase-maps  (map-indexed
                     (fn [ordinal phase]
                       {:tournament-phase/eid        (random-uuid)
                        :tournament-phase/ordinal    ordinal
                        :tournament-phase/phase-type (keyword (:phase-type phase))
                        :tournament-phase/rounds
                        (mapv (fn [r]
                                {:tournament-round/eid         (random-uuid)
                                 :tournament-round/round-index (:round-index r)
                                 :tournament-round/format      (or (:format r) 1)})
                              (:rounds phase))})
                     phases)]
    (dl/transact!
     conn
     (concat retract-ops
             [{:tournament/eid        eid
               :tournament/phases     (vec phase-maps)
               :tournament/updated-at (Date.)}]))))

;;; ─── Entry mutations ───────────────────────────────────────────────────────

(defn create-entry!
  "Register a player in a tournament. Returns the created entry, or nil
  when the player already holds one (one entry per player)."
  [conn eid player-sub]
  (when-not (entry-by-tournament-and-player conn eid player-sub)
    (let [entry-eid (random-uuid)]
      (dl/transact!
       conn
       [{:tournament-entry/eid        entry-eid
         :tournament-entry/tournament [:tournament/eid eid]
         :tournament-entry/player-sub player-sub
         :tournament-entry/created-at (Date.)}])
      (entry-by-tournament-and-player conn eid player-sub))))

(defn delete-entry!
  "Remove a player's entry from a tournament."
  [conn eid player-sub]
  (when-let [entry (entry-by-tournament-and-player conn eid player-sub)]
    (dl/transact! conn [[:db/retractEntity (dl/lookup-ref :tournament-entry/eid (:eid entry))]])))

;;; ─── Match mutations ───────────────────────────────────────────────────────

(defn create-match!
  "Create a match within a tournament. Enum-typed `:bracket-type`/`:status`
  are stored as keywords; the spec carries strings (or omits, defaulting
  to winners/pending)."
  [conn eid match-spec]
  (let [match-eid (random-uuid)
        now       (Date.)]
    (dl/transact!
     conn
     [{:match/eid            match-eid
       :match/tournament     [:tournament/eid eid]
       :match/phase-index    (:phase-index match-spec)
       :match/round-index    (:round-index match-spec)
       :match/bracket-type   (keyword (or (:bracket-type match-spec) "winners"))
       :match/player-one-sub (:player-one-sub match-spec)
       :match/player-two-sub (:player-two-sub match-spec)
       :match/status         :pending
       :match/format         (or (:format match-spec) 1)
       :match/created-at     now
       :match/updated-at     now}])
    (match-by-eid conn match-eid)))

(defn update-match-result!
  "Mark a match complete with a winner."
  [conn match-eid winner-sub]
  (dl/transact!
   conn
   [{:match/eid        match-eid
     :match/winner-sub winner-sub
     :match/status     :complete
     :match/updated-at (Date.)}])
  (match-by-eid conn match-eid))

;;; ─── Check-in mutations ────────────────────────────────────────────────────

(defn open-match-check-in!
  "Open (or extend) the series check-in window on a match, stamping
  `:check-in-opens-at` / `:check-in-closes-at`. Prior per-side check-ins are
  preserved — re-opening extends the window rather than resetting confirmations.
  Returns the transaction report; the caller synthesizes the post-write match
  from the pre-write entity it already holds rather than re-reading."
  [conn match-eid {:keys [opens-at closes-at]}]
  (dl/transact!
   conn
   [{:match/eid                match-eid
     :match/check-in-opens-at  (->date opens-at)
     :match/check-in-closes-at (->date closes-at)
     :match/updated-at         (Date.)}]))

(defn record-match-check-in!
  "Record a player's series check-in by stamping the timestamp for their
  side. `side` is `:player-one` or `:player-two`. Returns the transaction
  report; the caller synthesizes the post-write match from the pre-write
  entity it already holds rather than re-reading."
  [conn match-eid side checked-at]
  (let [attr (case side
               :player-one :match/player-one-checked-at
               :player-two :match/player-two-checked-at)]
    (dl/transact!
     conn
     [{:match/eid        match-eid
       attr              (->date checked-at)
       :match/updated-at (Date.)}])))

(defn set-match-lobby-code!
  "Stamp the series lobby code on a match. Issued once both sides check in
  and reused for every game of the series. Returns the transaction report;
  the caller synthesizes the post-write match from the pre-write entity it
  already holds rather than re-reading."
  [conn match-eid lobby-code]
  (dl/transact!
   conn
   [{:match/eid        match-eid
     :match/lobby-code lobby-code
     :match/updated-at (Date.)}]))

;;; ─── Game mutations ────────────────────────────────────────────────────────

(defn create-game!
  "Record a per-game result for a match, owned via `:match/games`. `opts`
  may carry `:replay-eid`, `:uploader-local-alliance-index`,
  `:player-one-draft-eid`, `:player-two-draft-eid`, which become refs to
  the `:replay` and per-side `:draft` entities."
  ([conn match-eid game-index winner-sub]
   (create-game! conn match-eid game-index winner-sub {}))
  ([conn match-eid game-index winner-sub
    {:keys [replay-eid uploader-local-alliance-index
            player-one-draft-eid player-two-draft-eid]}]
   (let [game-eid (random-uuid)
         game     (cond-> {:match-game/eid        game-eid
                           :match-game/game-index game-index
                           :match-game/winner-sub winner-sub
                           :match-game/created-at (Date.)}
                    replay-eid                          (assoc :match-game/replay [:replay/eid replay-eid])
                    (some? uploader-local-alliance-index) (assoc :match-game/uploader-local-alliance-index
                                                                 uploader-local-alliance-index)
                    player-one-draft-eid                (assoc :match-game/player-one-draft [:draft/eid player-one-draft-eid])
                    player-two-draft-eid                (assoc :match-game/player-two-draft [:draft/eid player-two-draft-eid]))]
     (dl/transact!
      conn
      [{:match/eid match-eid :match/games [game]}])
     {:eid                           game-eid
      :match-eid                     match-eid
      :game-index                    game-index
      :winner-sub                    winner-sub
      :replay-eid                    replay-eid
      :uploader-local-alliance-index uploader-local-alliance-index
      :player-one-draft-eid          player-one-draft-eid
      :player-two-draft-eid          player-two-draft-eid})))
