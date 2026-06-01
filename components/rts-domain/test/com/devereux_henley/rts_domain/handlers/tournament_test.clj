(ns com.devereux-henley.rts-domain.handlers.tournament-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.tournament :as handlers.tournament])
  (:import
   [java.time Instant LocalDateTime ZoneId]
   [java.util UUID]))

(def ^:private test-tournament-eid (UUID/fromString "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
(def ^:private test-game-eid (UUID/fromString "eea787d7-1065-45eb-a3f6-e26f32c294a1"))
;; Tournament reads/writes go through the Datalevin connection.
(def ^:private test-deps {:datalog-connection nil})

;; A tournament entity as the datalog data-access layer returns it.
(def ^:private test-tournament
  {:eid                       test-tournament-eid
   :name                      "Test Tournament"
   :description               "A test."
   :game-eid                  test-game-eid
   :created-by-sub            "dev-admin"
   :version                   1
   :status                    "registration"
   :registration-opens-at     nil
   :registration-closes-at    nil
   :timezone                  "UTC"
   :registration-closed-early false
   :current-phase-index       nil
   :qualifier-count           nil})

(def ^:private test-entry
  {:eid        (UUID/randomUUID) :tournament-eid test-tournament-eid
   :player-sub "dev-admin"       :created-at     (Instant/now)})

;; ─── get-tournament-by-eid ───────────────────────────────────────────────────

(deftest get-tournament-by-eid-assigns-type
  (with-redefs [data-access.contract/tournament-by-eid (fn [_ _] test-tournament)]
    (let [result (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)]
      (is (= :tournament/tournament (:type result))))))

(deftest get-tournament-by-eid-preserves-fields
  (with-redefs [data-access.contract/tournament-by-eid (fn [_ _] test-tournament)]
    (let [result (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)]
      (is (= test-tournament-eid (:eid result)))
      (is (= "Test Tournament" (:name result))))))

(deftest get-tournament-by-eid-returns-nil-when-not-found
  (with-redefs [data-access.contract/tournament-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)))))

;; ─── get-tournaments-for-game ────────────────────────────────────────────────

(deftest get-tournaments-for-game-assigns-type-to-each
  (with-redefs [data-access.contract/tournaments-for-game
                (fn [_ _] [test-tournament (assoc test-tournament :name "Second")])]
    (let [results (handlers.tournament/get-tournaments-for-game test-deps test-game-eid)]
      (is (every? #(= :tournament/tournament (:type %)) results)))))

(deftest get-tournaments-for-game-returns-all-results
  (with-redefs [data-access.contract/tournaments-for-game
                (fn [_ _] [test-tournament (assoc test-tournament :name "Second")])]
    (is (= 2 (count (handlers.tournament/get-tournaments-for-game test-deps test-game-eid))))))

(deftest get-tournaments-for-game-empty-result
  (with-redefs [data-access.contract/tournaments-for-game (fn [_ _] [])]
    (is (= [] (handlers.tournament/get-tournaments-for-game test-deps test-game-eid)))))

;; ─── get-tournament-state (reconstructed from entities) ──────────────────────

(deftest get-tournament-state-reconstructs-from-entity
  (with-redefs [data-access.contract/tournament-by-eid      (fn [_ _] test-tournament)
                data-access.contract/phases-for-tournament  (fn [_ _] [])
                data-access.contract/entries-for-tournament (fn [_ _] [])
                data-access.contract/matches-for-tournament (fn [_ _] [])]
    (let [result (handlers.tournament/get-tournament-state test-deps test-tournament-eid)]
      (is (= "registration" (:status result)))
      (is (= [] (:phases result))))))

(deftest get-tournament-state-returns-default-when-absent
  (with-redefs [data-access.contract/tournament-by-eid (fn [_ _] nil)]
    (let [result (handlers.tournament/get-tournament-state test-deps test-tournament-eid)]
      (is (= "registration" (:status result)))
      (is (= [] (:phases result))))))

;; ─── is-registration-open? ──────────────────────────────────────────────────
;;
;; Reconstructed state carries the registration window as instants/dates
;; (never strings), so the predicate is exercised with `Instant` values.

(deftest is-registration-open-true-when-within-window
  (let [state {:status       "registration"
               :registration {:opens-at     (Instant/parse "2020-01-01T00:00:00Z")
                              :closes-at    (Instant/parse "2030-01-01T00:00:00Z")
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (true? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-before-window
  (let [state {:status       "registration"
               :registration {:opens-at     (Instant/parse "2026-01-01T00:00:00Z")
                              :closes-at    (Instant/parse "2030-01-01T00:00:00Z")
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-after-window
  (let [state {:status       "registration"
               :registration {:opens-at     (Instant/parse "2020-01-01T00:00:00Z")
                              :closes-at    (Instant/parse "2025-01-01T00:00:00Z")
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-when-closed-early
  (let [state {:status       "registration"
               :registration {:opens-at     (Instant/parse "2020-01-01T00:00:00Z")
                              :closes-at    (Instant/parse "2030-01-01T00:00:00Z")
                              :closed-early true}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-when-status-not-registration
  (let [state {:status       "active"
               :registration {:opens-at     (Instant/parse "2020-01-01T00:00:00Z")
                              :closes-at    (Instant/parse "2030-01-01T00:00:00Z")
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-true-when-no-timestamps
  (let [state {:status       "registration"
               :registration {:opens-at nil :closes-at nil :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (true? (handlers.tournament/is-registration-open? state now)))))

;; ─── create-entry ────────────────────────────────────────────────────────────

(deftest create-entry-returns-entry-when-open
  (with-redefs [handlers.tournament/get-tournament-state
                (fn [_ _] {:status       "registration"
                           :registration {:opens-at nil :closes-at nil :closed-early false}})
                data-access.contract/create-entry!
                (fn [_ _ _] test-entry)]
    (let [result (handlers.tournament/create-entry test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/entry (:type result)))
      (is (= "dev-admin" (:player-sub result))))))

(deftest create-entry-returns-error-when-duplicate
  (with-redefs [handlers.tournament/get-tournament-state
                (fn [_ _] {:status       "registration"
                           :registration {:opens-at nil :closes-at nil :closed-early false}})
                ;; create-entry! returns nil when the player already holds an entry
                data-access.contract/create-entry!
                (fn [_ _ _] nil)]
    (let [result (handlers.tournament/create-entry test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/entry-error (:type result)))
      (is (= "Already entered in this tournament." (:message result))))))

(deftest create-entry-returns-error-when-closed
  (with-redefs [handlers.tournament/get-tournament-state
                (fn [_ _] {:status       "active"
                           :registration {:opens-at nil :closes-at nil :closed-early false}})]
    (let [result (handlers.tournament/create-entry test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/entry-error (:type result)))
      (is (= "Registration is not open." (:message result))))))

;; ─── delete-entry ────────────────────────────────────────────────────────────

(deftest delete-entry-returns-success-during-registration
  (with-redefs [handlers.tournament/get-tournament-state (fn [_ _] {:status "registration"})
                data-access.contract/delete-entry!       (fn [_ _ _] nil)]
    (let [result (handlers.tournament/delete-entry test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/entry-deleted (:type result))))))

(deftest delete-entry-returns-error-when-not-registration-status
  (with-redefs [handlers.tournament/get-tournament-state (fn [_ _] {:status "active"})]
    (let [result (handlers.tournament/delete-entry test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/entry-error (:type result))))))

;; ─── get-entries ─────────────────────────────────────────────────────────────

(deftest get-entries-assigns-type-to-each
  (with-redefs [data-access.contract/entries-for-tournament
                (fn [_ _] [test-entry (assoc test-entry :player-sub "dev-player-one")])]
    (let [results (handlers.tournament/get-entries test-deps test-tournament-eid)]
      (is (every? #(= :tournament/entry (:type %)) results)))))

(deftest get-entries-returns-all-results
  (with-redefs [data-access.contract/entries-for-tournament
                (fn [_ _] [test-entry (assoc test-entry :player-sub "dev-player-one")])]
    (is (= 2 (count (handlers.tournament/get-entries test-deps test-tournament-eid))))))

(deftest get-entries-empty-result
  (with-redefs [data-access.contract/entries-for-tournament (fn [_ _] [])]
    (is (= [] (handlers.tournament/get-entries test-deps test-tournament-eid)))))

;; ─── State transition handlers ──────────────────────────────────────────────
;;
;; Lifecycle handlers read the reconstructed state, write a targeted field
;; via `update-tournament!`, then re-read for the returned `:state`. The
;; tests back `tournament-by-eid` + `update-tournament!` with an atom so the
;; post-write re-read reflects the mutation; phases/entries/matches are
;; stubbed as needed for the standings derivation.

(defn- active-with [tournament-atom & {:keys [phases entries matches]}]
  {#'data-access.contract/tournament-by-eid      (fn [_ _] @tournament-atom)
   #'data-access.contract/update-tournament!     (fn [_ _ attrs] (swap! tournament-atom merge attrs) @tournament-atom)
   #'data-access.contract/phases-for-tournament  (fn [_ _] (or phases []))
   #'data-access.contract/entries-for-tournament (fn [_ _] (or entries []))
   #'data-access.contract/matches-for-tournament (fn [_ _] (or matches []))})

;; ─── start-tournament ────────────────────────────────────────────────────────

(deftest start-tournament-activates-and-points-at-first-phase
  (let [t (atom (assoc test-tournament :status "registration"))]
    (with-redefs-fn (active-with t
                                 :phases [{:phase-type "swiss" :rounds [{:round-index 0 :format 1}]}]
                                 :entries [{:player-sub "p1"} {:player-sub "p2"}])
      (fn []
        (let [result (handlers.tournament/start-tournament test-deps test-tournament-eid "dev-admin")]
          (is (= :tournament/started (:type result)))
          (is (= "active" (get-in result [:state :status])))
          (is (= 0 (get-in result [:state :current-phase])))
          (is (= 2 (count (get-in result [:state :standings])))))))))

(deftest start-tournament-rejects-non-organizer
  (with-redefs [data-access.contract/tournament-by-eid (fn [_ _] test-tournament)]
    (let [result (handlers.tournament/start-tournament test-deps test-tournament-eid "not-the-organizer")]
      (is (= :tournament/start-error (:type result)))
      (is (re-find #"organizer" (:message result))))))

(deftest start-tournament-rejects-wrong-status
  (let [t (atom (assoc test-tournament :status "active"))]
    (with-redefs-fn (active-with t)
      (fn []
        (let [result (handlers.tournament/start-tournament test-deps test-tournament-eid "dev-admin")]
          (is (= :tournament/start-error (:type result))))))))

(deftest start-tournament-not-found
  (with-redefs [data-access.contract/tournament-by-eid (fn [_ _] nil)]
    (let [result (handlers.tournament/start-tournament test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/start-error (:type result))))))

;; ─── complete-tournament ─────────────────────────────────────────────────────

(deftest complete-tournament-moves-active-to-complete
  (let [t (atom (assoc test-tournament :status "active"))]
    (with-redefs-fn (active-with t)
      (fn []
        (let [result (handlers.tournament/complete-tournament test-deps test-tournament-eid "dev-admin")]
          (is (= :tournament/completed (:type result)))
          (is (= "complete" (get-in result [:state :status]))))))))

(deftest complete-tournament-rejects-non-active
  (let [t (atom (assoc test-tournament :status "registration"))]
    (with-redefs-fn (active-with t)
      (fn []
        (let [result (handlers.tournament/complete-tournament test-deps test-tournament-eid "dev-admin")]
          (is (= :tournament/complete-error (:type result))))))))

;; ─── cancel-tournament ───────────────────────────────────────────────────────

(deftest cancel-tournament-from-registration
  (let [t (atom (assoc test-tournament :status "registration"))]
    (with-redefs-fn (active-with t)
      (fn []
        (let [result (handlers.tournament/cancel-tournament test-deps test-tournament-eid "dev-admin")]
          (is (= :tournament/cancelled (:type result)))
          (is (= "cancelled" (get-in result [:state :status]))))))))

(deftest cancel-tournament-from-active
  (let [t (atom (assoc test-tournament :status "active"))]
    (with-redefs-fn (active-with t)
      (fn []
        (let [result (handlers.tournament/cancel-tournament test-deps test-tournament-eid "dev-admin")]
          (is (= :tournament/cancelled (:type result))))))))

(deftest cancel-tournament-rejects-already-finished
  (let [t (atom (assoc test-tournament :status "complete"))]
    (with-redefs-fn (active-with t)
      (fn []
        (let [result (handlers.tournament/cancel-tournament test-deps test-tournament-eid "dev-admin")]
          (is (= :tournament/cancel-error (:type result))))))))

;; ─── close-registration-early ────────────────────────────────────────────────

(deftest close-registration-early-sets-flag
  (let [t (atom (assoc test-tournament :status "registration"))]
    (with-redefs-fn (active-with t)
      (fn []
        (let [result (handlers.tournament/close-registration-early test-deps test-tournament-eid "dev-admin")]
          (is (= :tournament/registration-closed (:type result)))
          (is (true? (get-in result [:state :registration :closed-early]))))))))

(deftest close-registration-early-rejects-non-organizer
  (with-redefs [data-access.contract/tournament-by-eid (fn [_ _] test-tournament)]
    (let [result (handlers.tournament/close-registration-early test-deps test-tournament-eid "not-the-organizer")]
      (is (= :tournament/registration-close-error (:type result))))))

(deftest close-registration-early-rejects-wrong-status
  (let [t (atom (assoc test-tournament :status "active"))]
    (with-redefs-fn (active-with t)
      (fn []
        (let [result (handlers.tournament/close-registration-early test-deps test-tournament-eid "dev-admin")]
          (is (= :tournament/registration-close-error (:type result))))))))

;; ─── create-match ────────────────────────────────────────────────────────────

(def ^:private test-match
  {:eid         (UUID/randomUUID) :tournament-eid test-tournament-eid
   :phase-index 0                 :round-index    0                   :player-one-sub "p1"      :player-two-sub "p2"
   :winner-sub  nil               :status         "pending"           :bracket-type   "winners" :format         1
   :created-at  (Instant/now)     :updated-at     (Instant/now)})

(deftest create-match-returns-match-when-active
  (with-redefs [handlers.tournament/get-tournament-state (fn [_ _] {:status "active"})
                data-access.contract/create-match!       (fn [_ _ _] test-match)]
    (let [result (handlers.tournament/create-match test-deps test-tournament-eid
                                                   {:phase-index 0 :round-index 0 :player-one-sub "p1" :player-two-sub "p2"})]
      (is (= :tournament/match (:type result)))
      (is (= "p1" (:player-one-sub result))))))

(deftest create-match-rejects-when-not-active
  (with-redefs [handlers.tournament/get-tournament-state (fn [_ _] {:status "registration"})]
    (let [result (handlers.tournament/create-match test-deps test-tournament-eid
                                                   {:phase-index 0 :round-index 0 :player-one-sub "p1" :player-two-sub "p2"})]
      (is (= :tournament/match-error (:type result))))))

;; ─── get-match-by-eid ────────────────────────────────────────────────────────

(deftest get-match-by-eid-assigns-type
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] test-match)]
    (let [result (handlers.tournament/get-match-by-eid test-deps (:eid test-match))]
      (is (= :tournament/match (:type result))))))

(deftest get-match-by-eid-returns-nil-when-not-found
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.tournament/get-match-by-eid test-deps (UUID/randomUUID))))))

;; ─── get-matches-for-tournament ──────────────────────────────────────────────

(deftest get-matches-for-tournament-assigns-type
  (with-redefs [data-access.contract/matches-for-tournament
                (fn [_ _] [test-match (assoc test-match :player-one-sub "p3")])]
    (let [results (handlers.tournament/get-matches-for-tournament test-deps test-tournament-eid)]
      (is (every? #(= :tournament/match (:type %)) results))
      (is (= 2 (count results))))))

;; ─── update-match-result ─────────────────────────────────────────────────────

(deftest update-match-result-updates-standings
  (with-redefs [data-access.contract/match-by-eid           (fn [_ _] test-match)
                data-access.contract/update-match-result!   (fn [_ _ _] nil)
                data-access.contract/tournament-by-eid      (fn [_ _] (assoc test-tournament :status "active"))
                data-access.contract/phases-for-tournament  (fn [_ _] [])
                data-access.contract/entries-for-tournament (fn [_ _] [{:player-sub "p1"} {:player-sub "p2"}])
                data-access.contract/matches-for-tournament
                (fn [_ _] [(assoc test-match :status "complete" :winner-sub "p1")])]
    (let [result (handlers.tournament/update-match-result test-deps (:eid test-match) "p1")]
      (is (= :tournament/match-result-recorded (:type result)))
      (is (= 2 (count (:standings result))))
      (is (= 3 (:points (first (filter #(= "p1" (:player-sub %)) (:standings result)))))))))

(deftest update-match-result-rejects-non-pending
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] (assoc test-match :status "complete"))]
    (let [result (handlers.tournament/update-match-result test-deps (:eid test-match) "p1")]
      (is (= :tournament/match-error (:type result))))))

(deftest update-match-result-not-found
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] nil)]
    (let [result (handlers.tournament/update-match-result test-deps (UUID/randomUUID) "p1")]
      (is (= :tournament/match-error (:type result))))))

;; ─── league/season resolution on create-tournament ──────────────────────────

(def ^:private test-league-eid (UUID/fromString "11111111-1111-1111-1111-111111111111"))
(def ^:private test-season-eid (UUID/fromString "22222222-2222-2222-2222-222222222222"))

(deftest create-tournament-resolves-league-from-season-eid
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/season-by-eid
                  (fn [_ _] {:eid test-season-eid :league-eid test-league-eid})
                  data-access.contract/create-tournament!
                  (fn [_ spec]
                    (reset! captured spec)
                    (assoc test-tournament :eid (:eid spec)
                           :league-eid test-league-eid
                           :season-eid test-season-eid))]
      (handlers.tournament/create-tournament
       test-deps
       {:eid                    (UUID/randomUUID)
        :game-eid               test-game-eid
        :season-eid             test-season-eid
        :name                   "Spring Cup"                                :description "x"
        :timezone               (ZoneId/of "UTC")
        :registration-opens-at  (LocalDateTime/parse "2026-04-01T00:00:00")
        :registration-closes-at (LocalDateTime/parse "2030-04-30T23:59:00")
        :created-by-sub         "dev-admin"                                 :version     1})
      (is (= test-league-eid (:league-eid @captured))
          "league-eid should be derived server-side from the season"))))

(deftest create-tournament-rejects-mismatched-league-and-season
  (with-redefs [data-access.contract/season-by-eid
                (fn [_ _] {:eid test-season-eid :league-eid test-league-eid})]
    (let [result (handlers.tournament/create-tournament
                  test-deps
                  {:eid                    (UUID/randomUUID)
                   :game-eid               test-game-eid
                   :league-eid             (UUID/fromString "33333333-3333-3333-3333-333333333333")
                   :season-eid             test-season-eid
                   :name                   "x"                                                      :description "x"
                   :timezone               (ZoneId/of "UTC")
                   :registration-opens-at  (LocalDateTime/parse "2026-04-01T00:00:00")
                   :registration-closes-at (LocalDateTime/parse "2030-04-30T23:59:00")
                   :created-by-sub         "dev-admin"                                              :version     1})]
      (is (= :tournament/create-error (:type result))))))

;; ─── tag-tournament passes nil league/season-eid through ──────────────────
;; Stripping happens in the schema model-transformer at the response boundary
;; (see com.devereux-henley.schema.contract/handle-model-transform); the
;; handler just preserves whatever the data layer hands it.

(deftest get-tournament-passes-non-nil-league-season-eids-through
  (with-redefs [data-access.contract/tournament-by-eid
                (fn [_ _] (assoc test-tournament :league-eid test-league-eid :season-eid test-season-eid))]
    (let [result (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)]
      (is (= test-league-eid (:league-eid result)))
      (is (= test-season-eid (:season-eid result))))))

;; --- tournament-view-model ---

(deftest tournament-view-model-shapes-data-state-and-viewer-flags
  (with-redefs [handlers.tournament/get-tournament-by-eid
                (fn [_ _] {:eid test-tournament-eid :name "Spring Open" :description "d" :created-by-sub "owner"})
                handlers.tournament/get-tournament-state
                (fn [_ _] {:status       "active"                                                      :phases [] :current-phase 0 :qualifier-count 1
                           :registration {:opens-at nil :closes-at nil}
                           :standings    [{:faction-name "A" :points 5} {:faction-name "B" :points 3}]})
                handlers.tournament/get-entries
                (fn [_ _] [{:player-sub "owner"}])
                handlers.tournament/get-matches-for-tournament
                (fn [_ _] [])]
    (let [result    (handlers.tournament/tournament-view-model test-deps test-tournament-eid "owner")
          standings (:standings (:tournament-state result))]
      (is (= "Spring Open" (get-in result [:data :name])))
      (is (true? (:is-organizer result)))
      (is (true? (:has-entry result)))
      (is (= [] (:matches-by-phase result)))
      (is (= [1 2] (mapv :rank standings)) "standings sorted by points desc and ranked")
      (is (= [true false] (mapv :advanced? standings)) "qualifier cut at rank 1"))))

(deftest tournament-view-model-viewer-flags-for-non-participant
  (with-redefs [handlers.tournament/get-tournament-by-eid
                (fn [_ _] {:eid test-tournament-eid :name "Spring Open" :created-by-sub "owner"})
                handlers.tournament/get-tournament-state
                (fn [_ _] {:status       "active"                       :phases    [] :current-phase 0 :qualifier-count nil
                           :registration {:opens-at nil :closes-at nil} :standings []})
                handlers.tournament/get-entries                (fn [_ _] [{:player-sub "owner"}])
                handlers.tournament/get-matches-for-tournament (fn [_ _] [])]
    (let [result (handlers.tournament/tournament-view-model test-deps test-tournament-eid "viewer")]
      (is (false? (:is-organizer result)))
      (is (false? (:has-entry result))))))

(deftest tournament-view-model-returns-missing-marker-when-absent
  (with-redefs [handlers.tournament/get-tournament-by-eid (fn [_ _] nil)]
    (is (= :missing/resource
           (:type (handlers.tournament/tournament-view-model test-deps test-tournament-eid "owner"))))))

;; --- organizer-view-model ---

;; A configured phase carries its rounds, so the progression logic can tell
;; the last round of a phase from a mid-phase round.
(def ^:private two-round-swiss
  [{:phase-type "swiss" :rounds [{:round-index 0} {:round-index 1}]}])

(def ^:private two-phase-config
  [{:phase-type "single-elimination" :rounds [{:round-index 0}]}
   {:phase-type "swiss" :rounds [{:round-index 0} {:round-index 1}]}])

(defn- organizer-redefs
  "Stubs the reads `organizer-view-model` (and the `tournament-view-model` it
   wraps) make: status, the configured phase vector, the active phase index,
   and the match list."
  [status phases current-phase matches]
  {#'handlers.tournament/get-tournament-by-eid      (fn [_ _] {:eid test-tournament-eid :name "Spring Open" :created-by-sub "owner"})
   #'handlers.tournament/get-tournament-state       (fn [_ _] {:status       status                         :phases    phases :current-phase current-phase :qualifier-count nil
                                                               :registration {:opens-at nil :closes-at nil} :standings []})
   #'handlers.tournament/get-entries                (fn [_ _] [{:player-sub "owner"}])
   #'handlers.tournament/get-matches-for-tournament (fn [_ _] matches)
   ;; tournament-view-model decorates each match with game counts via a real
   ;; db read; stub it out so the fixture stays at the handler boundary.
   #'handlers.tournament/get-games-for-match        (fn [_ _] [])})

(deftest organizer-view-model-generate-round-state-mid-phase
  (with-redefs-fn
    ;; round 0 of a two-round phase, 2 of 4 matches reported.
    (organizer-redefs "active" two-round-swiss 0
                      [{:phase-index 0 :round-index 0 :status "complete"}
                       {:phase-index 0 :round-index 0 :status "complete"}
                       {:phase-index 0 :round-index 0 :status "pending"}
                       {:phase-index 0 :round-index 0 :status "pending"}])
    #(let [result (handlers.tournament/organizer-view-model test-deps test-tournament-eid "owner")]
       (is (= "round" (:progress-state result)) "more rounds remain in the phase")
       (is (= 2 (:round-reported result)) "matches the bead's 2/4 example")
       (is (= 4 (:round-total result)))
       (is (false? (:can-progress? result)) "gated until the current round reports")
       (is (false? (:done? result))))))

(deftest organizer-view-model-generate-round-enabled-when-no-round-yet
  (with-redefs-fn
    (organizer-redefs "active" two-round-swiss 0 [])
    #(let [result (handlers.tournament/organizer-view-model test-deps test-tournament-eid "owner")]
       (is (= "round" (:progress-state result)))
       (is (true? (:can-progress? result)) "the first round can always be seeded"))))

(deftest organizer-view-model-advance-phase-state-on-last-round
  (with-redefs-fn
    ;; final (only) round of phase 0, fully reported, with a phase 1 to follow.
    (organizer-redefs "active" two-phase-config 0
                      [{:phase-index 0 :round-index 0 :status "complete"}
                       {:phase-index 0 :round-index 0 :status "complete"}])
    #(let [result (handlers.tournament/organizer-view-model test-deps test-tournament-eid "owner")]
       (is (= "phase" (:progress-state result)) "last round of the phase, more phases follow")
       (is (true? (:more-phases? result)))
       (is (= 2 (:next-phase-number result)) "1-based number of the phase advance moves into")
       (is (true? (:can-progress? result))))))

(deftest organizer-view-model-advance-phase-gated-until-reported
  (with-redefs-fn
    ;; same last-round-of-phase position, but only 2 of 4 reported.
    (organizer-redefs "active" two-phase-config 0
                      [{:phase-index 0 :round-index 0 :status "complete"}
                       {:phase-index 0 :round-index 0 :status "complete"}
                       {:phase-index 0 :round-index 0 :status "pending"}
                       {:phase-index 0 :round-index 0 :status "pending"}])
    #(let [result (handlers.tournament/organizer-view-model test-deps test-tournament-eid "owner")]
       (is (= "phase" (:progress-state result)))
       (is (false? (:can-progress? result)) "advance is gated until the final round reports"))))

(deftest organizer-view-model-done-state-on-final-round-of-final-phase
  (with-redefs-fn
    ;; only round of the only phase → nothing left to generate.
    (organizer-redefs "active" [{:phase-type "swiss" :rounds [{:round-index 0}]}] 0
                      [{:phase-index 0 :round-index 0 :status "pending"}])
    #(let [result (handlers.tournament/organizer-view-model test-deps test-tournament-eid "owner")]
       (is (= "done" (:progress-state result)))
       (is (true? (:done? result)))
       (is (false? (:can-progress? result)) "no further rounds or phases to generate"))))

(deftest organizer-view-model-inactive-when-not-active
  (with-redefs-fn
    (organizer-redefs "registration" [] nil [])
    #(let [result (handlers.tournament/organizer-view-model test-deps test-tournament-eid "owner")]
       (is (false? (:active? result)))
       (is (= "inactive" (:progress-state result)))
       (is (false? (:can-progress? result)))
       (is (false? (:done? result))))))

(deftest organizer-view-model-preserves-organizer-flag
  (with-redefs-fn
    (organizer-redefs "active" two-round-swiss 0 [])
    #(do
       (is (true? (:is-organizer (handlers.tournament/organizer-view-model test-deps test-tournament-eid "owner"))))
       (is (false? (:is-organizer (handlers.tournament/organizer-view-model test-deps test-tournament-eid "stranger")))))))

(deftest organizer-view-model-returns-missing-marker-when-absent
  (with-redefs [handlers.tournament/get-tournament-by-eid (fn [_ _] nil)]
    (is (= :missing/resource
           (:type (handlers.tournament/organizer-view-model test-deps test-tournament-eid "owner"))))))

;; ─── Series check-in ─────────────────────────────────────────────────────────
;;
;; check-in-state is a pure projection over the match's check-in timestamps;
;; open-check-in / check-in-player read the match, validate the caller, then
;; write a single timestamp field. Tests stub match-by-eid + the targeted
;; mutation; organizer-gated open-check-in also stubs tournament-by-eid.

(def ^:private checkin-match-eid (UUID/fromString "11111111-2222-3333-4444-555555555555"))

(def ^:private checkin-match
  {:eid            checkin-match-eid
   :tournament-eid test-tournament-eid
   :status         "pending"
   :player-one-sub "sigmar_42"
   :player-two-sub "chaos_undivided"
   :format         5})

(defn- minutes-from-now ^java.util.Date [mins]
  (java.util.Date/from (.plus (Instant/now) (java.time.Duration/ofMinutes mins))))

;; ── check-in-state ──

(deftest check-in-state-unopened-window
  (let [s (handlers.tournament/check-in-state checkin-match (Instant/now))]
    (is (false? (:opened? s)))
    (is (false? (:window-open? s)))
    (is (false? (:both-checked? s)))))

(deftest check-in-state-open-window
  (let [m (assoc checkin-match :check-in-opens-at (minutes-from-now 0)
                 :check-in-closes-at (minutes-from-now 10))
        s (handlers.tournament/check-in-state m (Instant/now))]
    (is (true? (:opened? s)))
    (is (true? (:window-open? s)))))

(deftest check-in-state-expired-window
  (let [m (assoc checkin-match :check-in-opens-at (minutes-from-now -20)
                 :check-in-closes-at (minutes-from-now -10))
        s (handlers.tournament/check-in-state m (Instant/now))]
    (is (true? (:opened? s)))
    (is (false? (:window-open? s)))))

(deftest check-in-state-future-open-not-yet-open
  ;; A future opens-at (scheduled-open path) must report the window closed until
  ;; now reaches the lower bound — the shared window-open? predicate enforces it.
  (let [m (assoc checkin-match :check-in-opens-at (minutes-from-now 10)
                 :check-in-closes-at (minutes-from-now 25))
        s (handlers.tournament/check-in-state m (Instant/now))]
    (is (true? (:opened? s)) "the window is configured")
    (is (false? (:window-open? s)) "but not open until now reaches opens-at")))

(deftest check-in-state-both-checked-signals-lobby
  (let [m (assoc checkin-match
                 :check-in-opens-at (minutes-from-now 0) :check-in-closes-at (minutes-from-now 10)
                 :player-one-checked-at (minutes-from-now 0) :player-two-checked-at (minutes-from-now 0))
        s (handlers.tournament/check-in-state m (Instant/now))]
    (is (true? (:player-one-checked? s)))
    (is (true? (:player-two-checked? s)))
    (is (true? (:both-checked? s)))))

;; ── open-check-in ──

(deftest open-check-in-opens-window-for-organizer
  (with-redefs [data-access.contract/match-by-eid         (fn [_ _] checkin-match)
                data-access.contract/tournament-by-eid    (fn [_ _] (assoc test-tournament :created-by-sub "dev-admin"))
                data-access.contract/open-match-check-in! (fn [_ _ {:keys [opens-at closes-at]}]
                                                            (assoc checkin-match
                                                                   :check-in-opens-at (java.util.Date/from opens-at)
                                                                   :check-in-closes-at (java.util.Date/from closes-at)))]
    (let [result (handlers.tournament/open-check-in test-deps checkin-match-eid "dev-admin")]
      (is (= :tournament/check-in-opened (:type result)))
      (is (= :tournament/match (get-in result [:match :type])))
      (is (true? (get-in result [:check-in :window-open?]))))))

(deftest open-check-in-rejects-non-organizer
  (with-redefs [data-access.contract/match-by-eid      (fn [_ _] checkin-match)
                data-access.contract/tournament-by-eid (fn [_ _] (assoc test-tournament :created-by-sub "dev-admin"))]
    (let [result (handlers.tournament/open-check-in test-deps checkin-match-eid "rando")]
      (is (= :tournament/check-in-error (:type result)))
      (is (re-find #"organizer" (:message result))))))

(deftest open-check-in-rejects-completed-match
  (with-redefs [data-access.contract/match-by-eid      (fn [_ _] (assoc checkin-match :status "complete"))
                data-access.contract/tournament-by-eid (fn [_ _] (assoc test-tournament :created-by-sub "dev-admin"))]
    (let [result (handlers.tournament/open-check-in test-deps checkin-match-eid "dev-admin")]
      (is (= :tournament/check-in-error (:type result)))
      (is (re-find #"completed" (:message result))))))

(deftest open-check-in-not-found
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] nil)]
    (let [result (handlers.tournament/open-check-in test-deps checkin-match-eid "dev-admin")]
      (is (= :tournament/check-in-error (:type result)))
      (is (re-find #"not found" (:message result))))))

;; ── check-in-player ──

(def ^:private open-checkin-match
  (assoc checkin-match
         :check-in-opens-at (minutes-from-now 0)
         :check-in-closes-at (minutes-from-now 10)))

(deftest check-in-player-records-participant
  (with-redefs [data-access.contract/match-by-eid           (fn [_ _] open-checkin-match)
                data-access.contract/record-match-check-in! (fn [_ _ side at]
                                                              (assoc open-checkin-match
                                                                     (case side
                                                                       :player-one :player-one-checked-at
                                                                       :player-two :player-two-checked-at)
                                                                     (java.util.Date/from at)))]
    (let [result (handlers.tournament/check-in-player test-deps checkin-match-eid "sigmar_42")]
      (is (= :tournament/checked-in (:type result)))
      (is (= :player-one (:side result)))
      (is (true? (get-in result [:check-in :player-one-checked?]))))))

(deftest check-in-player-second-side-maps-to-player-two
  (with-redefs [data-access.contract/match-by-eid           (fn [_ _] open-checkin-match)
                data-access.contract/record-match-check-in! (fn [_ _ _side at]
                                                              (assoc open-checkin-match :player-two-checked-at (java.util.Date/from at)))]
    (let [result (handlers.tournament/check-in-player test-deps checkin-match-eid "chaos_undivided")]
      (is (= :player-two (:side result))))))

(deftest check-in-player-rejects-non-participant
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] open-checkin-match)]
    (let [result (handlers.tournament/check-in-player test-deps checkin-match-eid "rando")]
      (is (= :tournament/check-in-error (:type result)))
      (is (re-find #"participant" (:message result))))))

(deftest check-in-player-rejects-when-window-closed
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] checkin-match)]
    (let [result (handlers.tournament/check-in-player test-deps checkin-match-eid "sigmar_42")]
      (is (= :tournament/check-in-error (:type result)))
      (is (re-find #"window" (:message result))))))

(deftest check-in-player-rejects-completed-match
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] (assoc open-checkin-match :status "complete"))]
    (let [result (handlers.tournament/check-in-player test-deps checkin-match-eid "sigmar_42")]
      (is (= :tournament/check-in-error (:type result))))))

(deftest check-in-player-idempotent-when-already-checked
  (with-redefs [data-access.contract/match-by-eid           (fn [_ _] (assoc open-checkin-match
                                                                             :player-one-checked-at (minutes-from-now 0)))
                data-access.contract/record-match-check-in! (fn [& _] (throw (ex-info "should not write on re-check" {})))]
    (let [result (handlers.tournament/check-in-player test-deps checkin-match-eid "sigmar_42")]
      (is (= :tournament/checked-in (:type result)))
      (is (true? (get-in result [:check-in :player-one-checked?]))))))

(deftest check-in-player-not-found
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] nil)]
    (is (= :tournament/check-in-error
           (:type (handlers.tournament/check-in-player test-deps checkin-match-eid "sigmar_42"))))))

;; ── get-check-in-state ──

(deftest get-check-in-state-returns-derived-state
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] open-checkin-match)]
    (let [s (handlers.tournament/get-check-in-state test-deps checkin-match-eid)]
      (is (true? (:window-open? s)))
      (is (false? (:both-checked? s))))))

(deftest get-check-in-state-nil-when-match-absent
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.tournament/get-check-in-state test-deps checkin-match-eid)))))

;; ── check-in regression coverage (code-review fixes) ──

(deftest check-in-state-completed-match-closes-window
  ;; A match that completes within its window must not still report open.
  (let [m (assoc checkin-match :status "complete"
                 :check-in-opens-at (minutes-from-now 0) :check-in-closes-at (minutes-from-now 10)
                 :player-one-checked-at (minutes-from-now 0) :player-two-checked-at (minutes-from-now 0))
        s (handlers.tournament/check-in-state m (Instant/now))]
    (is (false? (:window-open? s)) "completed match reports the window closed")))

(deftest open-check-in-rejects-bye
  (with-redefs [data-access.contract/match-by-eid      (fn [_ _] (assoc checkin-match :player-two-sub nil))
                data-access.contract/tournament-by-eid (fn [_ _] (assoc test-tournament :created-by-sub "dev-admin"))]
    (let [result (handlers.tournament/open-check-in test-deps checkin-match-eid "dev-admin")]
      (is (= :tournament/check-in-error (:type result)))
      (is (re-find #"bye" (:message result))))))

(deftest open-check-in-preserves-original-open-time-on-reopen
  (let [original (minutes-from-now -40)
        captured (atom nil)]
    (with-redefs [data-access.contract/match-by-eid         (fn [_ _] (assoc checkin-match
                                                                             :check-in-opens-at original
                                                                             :check-in-closes-at (minutes-from-now -25)))
                  data-access.contract/tournament-by-eid    (fn [_ _] (assoc test-tournament :created-by-sub "dev-admin"))
                  data-access.contract/open-match-check-in! (fn [_ _ window]
                                                              (reset! captured window)
                                                              (assoc checkin-match
                                                                     :check-in-opens-at (java.util.Date/from (:opens-at window))
                                                                     :check-in-closes-at (java.util.Date/from (:closes-at window))))]
      (let [result (handlers.tournament/open-check-in test-deps checkin-match-eid "dev-admin")]
        (is (= :tournament/check-in-opened (:type result)))
        (is (= (.toInstant original) (:opens-at @captured)) "re-open keeps the original open time")
        (is (.isAfter (:closes-at @captured) (Instant/now)) "re-open advances the close time")))))

(deftest check-in-player-idempotent-after-window-closes
  ;; A double-submit after the window lapses must not 422 a player already in.
  (with-redefs [data-access.contract/match-by-eid           (fn [_ _] (assoc open-checkin-match
                                                                             :check-in-closes-at (minutes-from-now -5)
                                                                             :player-one-checked-at (minutes-from-now -8)))
                data-access.contract/record-match-check-in! (fn [& _] (throw (ex-info "must not write" {})))]
    (let [result (handlers.tournament/check-in-player test-deps checkin-match-eid "sigmar_42")]
      (is (= :tournament/checked-in (:type result)) "already-checked player still succeeds after the window closes"))))

(deftest check-in-player-rejects-nil-user-sub
  ;; A nil user-sub (session with no presence check) must not satisfy the
  ;; participant gate — even on a bye where player-two-sub is itself nil.
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] (assoc open-checkin-match :player-two-sub nil))]
    (let [result (handlers.tournament/check-in-player test-deps checkin-match-eid nil)]
      (is (= :tournament/check-in-error (:type result)))
      (is (re-find #"participant" (:message result))))))

(deftest check-in-player-rejects-blank-user-sub
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] open-checkin-match)]
    (let [result (handlers.tournament/check-in-player test-deps checkin-match-eid "   ")]
      (is (= :tournament/check-in-error (:type result)))
      (is (re-find #"participant" (:message result))))))
