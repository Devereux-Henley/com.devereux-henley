(ns com.devereux-henley.rts-domain.handlers.tournament-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.tournament :as handlers.tournament])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def ^:private test-tournament-eid (UUID/fromString "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
(def ^:private test-game-eid (UUID/fromString "eea787d7-1065-45eb-a3f6-e26f32c294a1"))
(def ^:private test-deps {:connection nil})

(def ^:private test-tournament
  {:id       1             :eid            test-tournament-eid :name    "Test Tournament" :description "A test."
   :game-eid test-game-eid :created-by-sub "dev-admin"         :version 1})

(def ^:private test-entry
  {:id         1           :eid        (UUID/randomUUID) :tournament-eid test-tournament-eid
   :player-sub "dev-admin" :created-at (Instant/now)     :deleted-at     nil})

;; ─── get-tournament-by-eid ───────────────────────────────────────────────────

(deftest get-tournament-by-eid-assigns-type
  (with-redefs [data-access.contract/get-tournament-by-eid (fn [_ _] test-tournament)]
    (let [result (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)]
      (is (= :tournament/tournament (:type result))))))

(deftest get-tournament-by-eid-preserves-fields
  (with-redefs [data-access.contract/get-tournament-by-eid (fn [_ _] test-tournament)]
    (let [result (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)]
      (is (= test-tournament-eid (:eid result)))
      (is (= "Test Tournament" (:name result))))))

(deftest get-tournament-by-eid-returns-nil-when-not-found
  (with-redefs [data-access.contract/get-tournament-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)))))

;; ─── get-tournaments-for-game ────────────────────────────────────────────────

(deftest get-tournaments-for-game-assigns-type-to-each
  (with-redefs [data-access.contract/get-tournaments-for-game
                (fn [_ _] [test-tournament (assoc test-tournament :name "Second")])]
    (let [results (handlers.tournament/get-tournaments-for-game test-deps test-game-eid)]
      (is (every? #(= :tournament/tournament (:type %)) results)))))

(deftest get-tournaments-for-game-returns-all-results
  (with-redefs [data-access.contract/get-tournaments-for-game
                (fn [_ _] [test-tournament (assoc test-tournament :name "Second")])]
    (is (= 2 (count (handlers.tournament/get-tournaments-for-game test-deps test-game-eid))))))

(deftest get-tournaments-for-game-empty-result
  (with-redefs [data-access.contract/get-tournaments-for-game (fn [_ _] [])]
    (is (= [] (handlers.tournament/get-tournaments-for-game test-deps test-game-eid)))))

;; ─── get-tournament-state ────────────────────────────────────────────────────

(deftest get-tournament-state-parses-json
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"registration\"}" :updated-at (Instant/now)})]
    (let [result (handlers.tournament/get-tournament-state test-deps test-tournament-eid)]
      (is (= "registration" (:status result))))))

(deftest get-tournament-state-returns-default-when-nil
  (with-redefs [data-access.contract/get-tournament-state (fn [_ _] nil)]
    (let [result (handlers.tournament/get-tournament-state test-deps test-tournament-eid)]
      (is (= "registration" (:status result)))
      (is (= [] (:phases result))))))

;; ─── is-registration-open? ──────────────────────────────────────────────────

(deftest is-registration-open-true-when-within-window
  (let [state {:status       "registration"
               :registration {:opens-at     "2020-01-01T00:00:00Z"
                              :closes-at    "2030-01-01T00:00:00Z"
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (true? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-before-window
  (let [state {:status       "registration"
               :registration {:opens-at     "2026-01-01T00:00:00Z"
                              :closes-at    "2030-01-01T00:00:00Z"
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-after-window
  (let [state {:status       "registration"
               :registration {:opens-at     "2020-01-01T00:00:00Z"
                              :closes-at    "2025-01-01T00:00:00Z"
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-when-closed-early
  (let [state {:status       "registration"
               :registration {:opens-at     "2020-01-01T00:00:00Z"
                              :closes-at    "2030-01-01T00:00:00Z"
                              :closed-early true}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-when-status-not-registration
  (let [state {:status       "active"
               :registration {:opens-at     "2020-01-01T00:00:00Z"
                              :closes-at    "2030-01-01T00:00:00Z"
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
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"registration\",\"registration\":{\"opens-at\":\"2020-01-01T00:00:00Z\",\"closes-at\":\"2030-01-01T00:00:00Z\",\"closed-early\":false}}" :updated-at (Instant/now)})
                data-access.contract/create-entry
                (fn [_ _ _] test-entry)]
    (let [result (handlers.tournament/create-entry test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/entry (:type result)))
      (is (= "dev-admin" (:player-sub result))))))

(deftest create-entry-returns-error-when-closed
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"active\",\"registration\":{\"opens-at\":\"2020-01-01T00:00:00Z\",\"closes-at\":\"2025-01-01T00:00:00Z\",\"closed-early\":false}}" :updated-at (Instant/now)})]
    (let [result (handlers.tournament/create-entry test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/entry-error (:type result)))
      (is (= "Registration is not open." (:message result))))))

;; ─── delete-entry ────────────────────────────────────────────────────────────

(deftest delete-entry-returns-success-during-registration
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"registration\"}" :updated-at (Instant/now)})
                data-access.contract/delete-entry         (fn [_ _ _] nil)]
    (let [result (handlers.tournament/delete-entry test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/entry-deleted (:type result))))))

(deftest delete-entry-returns-error-when-not-registration-status
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"active\"}" :updated-at (Instant/now)})]
    (let [result (handlers.tournament/delete-entry test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/entry-error (:type result))))))

;; ─── get-entries ─────────────────────────────────────────────────────────────

(deftest get-entries-assigns-type-to-each
  (with-redefs [data-access.contract/get-entries-for-tournament
                (fn [_ _] [test-entry (assoc test-entry :player-sub "dev-player-one")])]
    (let [results (handlers.tournament/get-entries test-deps test-tournament-eid)]
      (is (every? #(= :tournament/entry (:type %)) results)))))

(deftest get-entries-returns-all-results
  (with-redefs [data-access.contract/get-entries-for-tournament
                (fn [_ _] [test-entry (assoc test-entry :player-sub "dev-player-one")])]
    (is (= 2 (count (handlers.tournament/get-entries test-deps test-tournament-eid))))))

(deftest get-entries-empty-result
  (with-redefs [data-access.contract/get-entries-for-tournament (fn [_ _] [])]
    (is (= [] (handlers.tournament/get-entries test-deps test-tournament-eid)))))

;; ─── advance-tournament ──────────────────────────────────────────────────────

(def ^:private registration-state-json
  "{\"status\":\"registration\",\"registration\":{\"opens-at\":\"2020-01-01T00:00:00Z\",\"closes-at\":\"2030-01-01T00:00:00Z\",\"closed-early\":false},\"standings\":[],\"phases\":[]}")

(deftest advance-tournament-to-active-populates-standings
  (with-redefs [data-access.contract/get-tournament-by-eid   (fn [_ _] test-tournament)
                data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state registration-state-json :updated-at (Instant/now)})
                data-access.contract/get-entries-for-tournament
                (fn [_ _] [{:player-sub "p1"} {:player-sub "p2"}])
                data-access.contract/upsert-tournament-state (fn [_ _ _] nil)]
    (let [result (handlers.tournament/advance-tournament test-deps test-tournament-eid "active" "dev-admin")]
      (is (= :tournament/advance-success (:type result)))
      (is (= "active" (get-in result [:state :status])))
      (is (= 2 (count (get-in result [:state :standings])))))))

(deftest advance-tournament-rejects-non-organizer
  (with-redefs [data-access.contract/get-tournament-by-eid (fn [_ _] test-tournament)]
    (let [result (handlers.tournament/advance-tournament test-deps test-tournament-eid "active" "not-the-organizer")]
      (is (= :tournament/advance-error (:type result)))
      (is (re-find #"organizer" (:message result))))))

(deftest advance-tournament-rejects-invalid-transition
  (with-redefs [data-access.contract/get-tournament-by-eid (fn [_ _] test-tournament)
                data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state registration-state-json :updated-at (Instant/now)})]
    (let [result (handlers.tournament/advance-tournament test-deps test-tournament-eid "complete" "dev-admin")]
      (is (= :tournament/transition-error (:type result))))))

(deftest advance-tournament-not-found
  (with-redefs [data-access.contract/get-tournament-by-eid (fn [_ _] nil)]
    (let [result (handlers.tournament/advance-tournament test-deps test-tournament-eid "active" "dev-admin")]
      (is (= :tournament/advance-error (:type result))))))

;; ─── close-registration-early ────────────────────────────────────────────────

(deftest close-registration-early-sets-flag
  (with-redefs [data-access.contract/get-tournament-by-eid   (fn [_ _] test-tournament)
                data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state registration-state-json :updated-at (Instant/now)})
                data-access.contract/upsert-tournament-state (fn [_ _ _] nil)]
    (let [result (handlers.tournament/close-registration-early test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/close-registration-success (:type result)))
      (is (true? (get-in result [:state :registration :closed-early]))))))

(deftest close-registration-early-rejects-non-organizer
  (with-redefs [data-access.contract/get-tournament-by-eid (fn [_ _] test-tournament)]
    (let [result (handlers.tournament/close-registration-early test-deps test-tournament-eid "not-the-organizer")]
      (is (= :tournament/advance-error (:type result))))))

(deftest close-registration-early-rejects-wrong-status
  (with-redefs [data-access.contract/get-tournament-by-eid (fn [_ _] test-tournament)
                data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"active\"}" :updated-at (Instant/now)})]
    (let [result (handlers.tournament/close-registration-early test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/advance-error (:type result))))))

;; ─── create-match ────────────────────────────────────────────────────────────

(def ^:private active-state-json
  "{\"status\":\"active\",\"standings\":[{\"player-sub\":\"p1\",\"wins\":0,\"losses\":0,\"draws\":0,\"points\":0},{\"player-sub\":\"p2\",\"wins\":0,\"losses\":0,\"draws\":0,\"points\":0}],\"phases\":[]}")

(def ^:private test-match
  {:id          1   :eid         (UUID/randomUUID) :tournament-eid test-tournament-eid
   :phase-index 0   :round-index 0                 :player-one-sub "p1"                :player-two-sub "p2"
   :winner-sub  nil :status      "pending"         :created-at     (Instant/now)       :updated-at     (Instant/now)})

(deftest create-match-returns-match-when-active
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state active-state-json :updated-at (Instant/now)})
                data-access.contract/create-match
                (fn [_ _ _] test-match)]
    (let [result (handlers.tournament/create-match test-deps test-tournament-eid
                                                   {:phase-index 0 :round-index 0 :player-one-sub "p1" :player-two-sub "p2"})]
      (is (= :tournament/match (:type result)))
      (is (= "p1" (:player-one-sub result))))))

(deftest create-match-rejects-when-not-active
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state registration-state-json :updated-at (Instant/now)})]
    (let [result (handlers.tournament/create-match test-deps test-tournament-eid
                                                   {:phase-index 0 :round-index 0 :player-one-sub "p1" :player-two-sub "p2"})]
      (is (= :tournament/match-error (:type result))))))

;; ─── get-match-by-eid ────────────────────────────────────────────────────────

(deftest get-match-by-eid-assigns-type
  (with-redefs [data-access.contract/get-match-by-eid (fn [_ _] test-match)]
    (let [result (handlers.tournament/get-match-by-eid test-deps (:eid test-match))]
      (is (= :tournament/match (:type result))))))

(deftest get-match-by-eid-returns-nil-when-not-found
  (with-redefs [data-access.contract/get-match-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.tournament/get-match-by-eid test-deps (UUID/randomUUID))))))

;; ─── get-matches-for-tournament ──────────────────────────────────────────────

(deftest get-matches-for-tournament-assigns-type
  (with-redefs [data-access.contract/get-matches-for-tournament
                (fn [_ _] [test-match (assoc test-match :player-one-sub "p3")])]
    (let [results (handlers.tournament/get-matches-for-tournament test-deps test-tournament-eid)]
      (is (every? #(= :tournament/match (:type %)) results))
      (is (= 2 (count results))))))

;; ─── update-match-result ─────────────────────────────────────────────────────

(deftest update-match-result-updates-standings
  (with-redefs [data-access.contract/get-match-by-eid        (fn [_ _] test-match)
                data-access.contract/update-match-result     (fn [_ _ _] nil)
                data-access.contract/get-matches-for-tournament
                (fn [_ _] [(assoc test-match :status "complete" :winner-sub "p1")])
                data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state active-state-json :updated-at (Instant/now)})
                data-access.contract/upsert-tournament-state (fn [_ _ _] nil)]
    (let [result (handlers.tournament/update-match-result test-deps (:eid test-match) "p1")]
      (is (= :tournament/match-result-recorded (:type result)))
      (is (= 2 (count (:standings result))))
      (is (= 3 (:points (first (filter #(= "p1" (:player-sub %)) (:standings result)))))))))

(deftest update-match-result-rejects-non-pending
  (with-redefs [data-access.contract/get-match-by-eid (fn [_ _] (assoc test-match :status "complete"))]
    (let [result (handlers.tournament/update-match-result test-deps (:eid test-match) "p1")]
      (is (= :tournament/match-error (:type result))))))

(deftest update-match-result-not-found
  (with-redefs [data-access.contract/get-match-by-eid (fn [_ _] nil)]
    (let [result (handlers.tournament/update-match-result test-deps (UUID/randomUUID) "p1")]
      (is (= :tournament/match-error (:type result))))))
