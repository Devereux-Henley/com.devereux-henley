(ns com.devereux-henley.rts-domain.rules.tournament-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rts-domain.rules.tournament :as rules]))

;; ─── validate-transition ─────────────────────────────────────────────────────

(deftest valid-transition-registration-to-active
  (is (nil? (rules/validate-transition "registration" "active"))))

(deftest valid-transition-registration-to-cancelled
  (is (nil? (rules/validate-transition "registration" "cancelled"))))

(deftest valid-transition-active-to-complete
  (is (nil? (rules/validate-transition "active" "complete"))))

(deftest valid-transition-active-to-cancelled
  (is (nil? (rules/validate-transition "active" "cancelled"))))

(deftest invalid-transition-registration-to-complete
  (let [error (rules/validate-transition "registration" "complete")]
    (is (= :tournament/transition-error (:type error)))))

(deftest invalid-transition-active-to-registration
  (let [error (rules/validate-transition "active" "registration")]
    (is (= :tournament/transition-error (:type error)))))

(deftest invalid-transition-complete-to-anything
  (is (some? (rules/validate-transition "complete" "active")))
  (is (some? (rules/validate-transition "complete" "registration"))))

(deftest invalid-transition-cancelled-to-anything
  (is (some? (rules/validate-transition "cancelled" "active")))
  (is (some? (rules/validate-transition "cancelled" "registration"))))

;; ─── close-registration ─────────────────────────────────────────────────────

(deftest close-registration-sets-active-status
  (let [state   {:status "registration" :standings [] :phases []}
        entries [{:player-sub "player-a"} {:player-sub "player-b"}]
        result  (rules/close-registration state entries)]
    (is (= "active" (:status result)))))

(deftest close-registration-populates-standings
  (let [state   {:status "registration" :standings [] :phases []}
        entries [{:player-sub "player-a"} {:player-sub "player-b"}]
        result  (rules/close-registration state entries)]
    (is (= 2 (count (:standings result))))
    (is (= "player-a" (:player-sub (first (:standings result)))))
    (is (= 0 (:wins (first (:standings result)))))
    (is (= 0 (:points (first (:standings result)))))))

(deftest close-registration-empty-entries
  (let [state  {:status "registration" :standings [] :phases []}
        result (rules/close-registration state [])]
    (is (= "active" (:status result)))
    (is (= [] (:standings result)))))

(deftest close-registration-sets-current-phase-when-phases-exist
  (let [state  {:status "registration" :standings [] :phases [{:phase-type "swiss"}]}
        result (rules/close-registration state [{:player-sub "p1"}])]
    (is (= 0 (:current-phase result)))))

(deftest close-registration-nil-current-phase-when-no-phases
  (let [state  {:status "registration" :standings [] :phases []}
        result (rules/close-registration state [{:player-sub "p1"}])]
    (is (nil? (:current-phase result)))))
