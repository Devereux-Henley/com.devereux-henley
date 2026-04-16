(ns com.devereux-henley.rts-domain.handlers.tournament-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.tournament :as handlers.tournament])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def ^:private test-tournament-eid (UUID/fromString "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
(def ^:private test-game-eid (UUID/fromString "eea787d7-1065-45eb-a3f6-e26f32c294a1"))
(def ^:private test-deps {:connection nil})

(def ^:private test-tournament
  {:id 1 :eid test-tournament-eid :name "Test Tournament" :description "A test."
   :game-eid test-game-eid :created-by-sub "dev-admin" :version 1})

(def ^:private test-registration
  {:id 1 :eid (UUID/randomUUID) :tournament-eid test-tournament-eid
   :player-sub "dev-admin" :registered-at (Instant/now) :withdrawn-at nil})

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
  (let [state {:status "registration"
               :registration {:opens-at "2020-01-01T00:00:00Z"
                              :closes-at "2030-01-01T00:00:00Z"
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (true? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-before-window
  (let [state {:status "registration"
               :registration {:opens-at "2026-01-01T00:00:00Z"
                              :closes-at "2030-01-01T00:00:00Z"
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-after-window
  (let [state {:status "registration"
               :registration {:opens-at "2020-01-01T00:00:00Z"
                              :closes-at "2025-01-01T00:00:00Z"
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-when-closed-early
  (let [state {:status "registration"
               :registration {:opens-at "2020-01-01T00:00:00Z"
                              :closes-at "2030-01-01T00:00:00Z"
                              :closed-early true}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-false-when-status-not-registration
  (let [state {:status "active"
               :registration {:opens-at "2020-01-01T00:00:00Z"
                              :closes-at "2030-01-01T00:00:00Z"
                              :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (false? (handlers.tournament/is-registration-open? state now)))))

(deftest is-registration-open-true-when-no-timestamps
  (let [state {:status "registration"
               :registration {:opens-at nil :closes-at nil :closed-early false}}
        now   (Instant/parse "2025-06-01T00:00:00Z")]
    (is (true? (handlers.tournament/is-registration-open? state now)))))

;; ─── register-player ─────────────────────────────────────────────────────────

(deftest register-player-returns-registration-when-open
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"registration\",\"registration\":{\"opens-at\":\"2020-01-01T00:00:00Z\",\"closes-at\":\"2030-01-01T00:00:00Z\",\"closed-early\":false}}" :updated-at (Instant/now)})
                data-access.contract/register-player
                (fn [_ _ _] test-registration)]
    (let [result (handlers.tournament/register-player test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/registration (:type result)))
      (is (= "dev-admin" (:player-sub result))))))

(deftest register-player-returns-error-when-closed
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"active\",\"registration\":{\"opens-at\":\"2020-01-01T00:00:00Z\",\"closes-at\":\"2025-01-01T00:00:00Z\",\"closed-early\":false}}" :updated-at (Instant/now)})]
    (let [result (handlers.tournament/register-player test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/registration-error (:type result)))
      (is (= "Registration is not open." (:message result))))))

;; ─── withdraw-player ─────────────────────────────────────────────────────────

(deftest withdraw-player-returns-success-during-registration
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"registration\"}" :updated-at (Instant/now)})
                data-access.contract/withdraw-player (fn [_ _ _] nil)]
    (let [result (handlers.tournament/withdraw-player test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/withdraw-success (:type result))))))

(deftest withdraw-player-returns-error-when-not-registration-status
  (with-redefs [data-access.contract/get-tournament-state
                (fn [_ _] {:id 1 :state "{\"status\":\"active\"}" :updated-at (Instant/now)})]
    (let [result (handlers.tournament/withdraw-player test-deps test-tournament-eid "dev-admin")]
      (is (= :tournament/registration-error (:type result))))))

;; ─── get-registrations ───────────────────────────────────────────────────────

(deftest get-registrations-assigns-type-to-each
  (with-redefs [data-access.contract/get-registrations-for-tournament
                (fn [_ _] [test-registration (assoc test-registration :player-sub "dev-player-one")])]
    (let [results (handlers.tournament/get-registrations test-deps test-tournament-eid)]
      (is (every? #(= :tournament/registration (:type %)) results)))))

(deftest get-registrations-returns-all-results
  (with-redefs [data-access.contract/get-registrations-for-tournament
                (fn [_ _] [test-registration (assoc test-registration :player-sub "dev-player-one")])]
    (is (= 2 (count (handlers.tournament/get-registrations test-deps test-tournament-eid))))))

(deftest get-registrations-empty-result
  (with-redefs [data-access.contract/get-registrations-for-tournament (fn [_ _] [])]
    (is (= [] (handlers.tournament/get-registrations test-deps test-tournament-eid)))))
