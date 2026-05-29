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
