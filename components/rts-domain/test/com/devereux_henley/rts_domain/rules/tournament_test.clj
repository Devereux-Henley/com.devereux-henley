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

;; ─── recalculate-standings ───────────────────────────────────────────────────

(deftest recalculate-standings-win
  (let [standings [{:player-sub "p1" :wins 0 :losses 0 :draws 0 :points 0}
                   {:player-sub "p2" :wins 0 :losses 0 :draws 0 :points 0}]
        matches   [{:player-one-sub "p1" :player-two-sub "p2" :winner-sub "p1"}]
        result    (rules/recalculate-standings standings matches)]
    (is (= 1 (:wins (first result))))
    (is (= 3 (:points (first result))))
    (is (= 1 (:losses (second result))))
    (is (= 0 (:points (second result))))))

(deftest recalculate-standings-draw
  (let [standings [{:player-sub "p1" :wins 0 :losses 0 :draws 0 :points 0}
                   {:player-sub "p2" :wins 0 :losses 0 :draws 0 :points 0}]
        matches   [{:player-one-sub "p1" :player-two-sub "p2" :winner-sub "draw"}]
        result    (rules/recalculate-standings standings matches)]
    (is (= 1 (:draws (first result))))
    (is (= 1 (:points (first result))))
    (is (= 1 (:draws (second result))))
    (is (= 1 (:points (second result))))))

(deftest recalculate-standings-bye-ignored
  (let [standings [{:player-sub "p1" :wins 0 :losses 0 :draws 0 :points 0}]
        matches   [{:player-one-sub "p1" :player-two-sub nil :winner-sub "p1"}]
        result    (rules/recalculate-standings standings matches)]
    (is (= 0 (:wins (first result))))))

(deftest recalculate-standings-multiple-matches
  (let [standings [{:player-sub "p1" :wins 0 :losses 0 :draws 0 :points 0}
                   {:player-sub "p2" :wins 0 :losses 0 :draws 0 :points 0}]
        matches   [{:player-one-sub "p1" :player-two-sub "p2" :winner-sub "p1"}
                   {:player-one-sub "p2" :player-two-sub "p1" :winner-sub "p2"}]
        result    (rules/recalculate-standings standings matches)]
    (is (= 1 (:wins (first result))))
    (is (= 1 (:losses (first result))))
    (is (= 3 (:points (first result))))
    (is (= 1 (:wins (second result))))
    (is (= 1 (:losses (second result))))
    (is (= 3 (:points (second result))))))

;; ─── match-win-threshold ─────────────────────────────────────────────────────

(deftest match-win-threshold-bo1
  (is (= 1 (rules/match-win-threshold 1))))

(deftest match-win-threshold-bo3
  (is (= 2 (rules/match-win-threshold 3))))

(deftest match-win-threshold-bo5
  (is (= 3 (rules/match-win-threshold 5))))

;; ─── check-match-complete ────────────────────────────────────────────────────

(deftest check-match-complete-bo1-win
  (is (= "p1" (rules/check-match-complete [{:winner-sub "p1"}] 1))))

(deftest check-match-complete-bo3-not-yet
  (is (nil? (rules/check-match-complete [{:winner-sub "p1"}] 3))))

(deftest check-match-complete-bo3-win
  (is (= "p1" (rules/check-match-complete [{:winner-sub "p1"} {:winner-sub "p2"} {:winner-sub "p1"}] 3))))

(deftest check-match-complete-bo5-win
  (is (= "p2" (rules/check-match-complete
               [{:winner-sub "p1"} {:winner-sub "p2"} {:winner-sub "p2"} {:winner-sub "p2"}] 5))))

;; ─── swiss-pair ──────────────────────────────────────────────────────────────

(deftest swiss-pair-basic
  (let [standings [{:player-sub "p1" :points 3}
                   {:player-sub "p2" :points 0}]
        result    (rules/swiss-pair standings [])]
    (is (= 1 (count result)))
    (is (= "p1" (:player-one-sub (first result))))
    (is (= "p2" (:player-two-sub (first result))))))

(deftest swiss-pair-avoids-repeat
  (let [standings [{:player-sub "p1" :points 3}
                   {:player-sub "p2" :points 3}
                   {:player-sub "p3" :points 0}
                   {:player-sub "p4" :points 0}]
        history   [{:player-one-sub "p1" :player-two-sub "p2"}]
        result    (rules/swiss-pair standings history)]
    (is (= 2 (count result)))
    ;; p1 should not be paired with p2 again
    (is (not (some #(= #{(:player-one-sub %) (:player-two-sub %)} #{"p1" "p2"}) result)))))

(deftest swiss-pair-odd-players-bye
  (let [standings [{:player-sub "p1" :points 3}
                   {:player-sub "p2" :points 0}
                   {:player-sub "p3" :points 0}]
        result    (rules/swiss-pair standings [])]
    (is (= 2 (count result)))
    (is (some #(nil? (:player-two-sub %)) result))))

;; ─── generate-elimination-bracket ───────────────────────────────────────────

(deftest elimination-bracket-4-players
  (let [result (rules/generate-elimination-bracket ["s1" "s2" "s3" "s4"])]
    (is (= 2 (count result)))
    (is (= "s1" (:player-one-sub (first result))))
    (is (= "s4" (:player-two-sub (first result))))
    (is (= "s2" (:player-one-sub (second result))))
    (is (= "s3" (:player-two-sub (second result))))))

(deftest elimination-bracket-3-players-bye
  (let [result (rules/generate-elimination-bracket ["s1" "s2" "s3"])]
    (is (= 2 (count result)))
    ;; Top seed gets a bye (nil player-two-sub)
    (is (some #(nil? (:player-two-sub %)) result))))

(deftest elimination-bracket-2-players
  (let [result (rules/generate-elimination-bracket ["s1" "s2"])]
    (is (= 1 (count result)))
    (is (= "s1" (:player-one-sub (first result))))
    (is (= "s2" (:player-two-sub (first result))))))

;; ─── Double elimination counts ───────────────────────────────────────────────

(deftest winners-bracket-round-count-power-of-two
  (is (= 1 (rules/winners-bracket-round-count 2)))
  (is (= 2 (rules/winners-bracket-round-count 4)))
  (is (= 3 (rules/winners-bracket-round-count 8)))
  (is (= 4 (rules/winners-bracket-round-count 16))))

(deftest winners-bracket-round-count-non-power-of-two
  (is (= 3 (rules/winners-bracket-round-count 5)))
  (is (= 3 (rules/winners-bracket-round-count 6)))
  (is (= 3 (rules/winners-bracket-round-count 7))))

(deftest losers-bracket-round-count-power-of-two
  (is (= 0 (rules/losers-bracket-round-count 2)))
  (is (= 2 (rules/losers-bracket-round-count 4)))
  (is (= 4 (rules/losers-bracket-round-count 8)))
  (is (= 6 (rules/losers-bracket-round-count 16))))

;; ─── advance-winners-bracket-round ──────────────────────────────────────────

(deftest advance-winners-pairs-adjacent-winners
  (let [prev   [{:status "complete" :winner-sub "a" :player-one-sub "a" :player-two-sub "x"}
                {:status "complete" :winner-sub "b" :player-one-sub "b" :player-two-sub "y"}
                {:status "complete" :winner-sub "c" :player-one-sub "c" :player-two-sub "z"}
                {:status "complete" :winner-sub "d" :player-one-sub "d" :player-two-sub "w"}]
        result (rules/advance-winners-bracket-round prev)]
    (is (= 2 (count result)))
    (is (= "a" (:player-one-sub (first result))))
    (is (= "b" (:player-two-sub (first result))))
    (is (= "c" (:player-one-sub (second result))))
    (is (= "d" (:player-two-sub (second result))))))

(deftest advance-winners-skips-incomplete-matches
  (let [prev   [{:status "complete" :winner-sub "a" :player-one-sub "a" :player-two-sub "x"}
                {:status "pending" :winner-sub nil :player-one-sub "b" :player-two-sub "y"}]
        result (rules/advance-winners-bracket-round prev)]
    (is (= 1 (count result)))
    (is (= "a" (:player-one-sub (first result))))
    (is (nil? (:player-two-sub (first result))))))

;; ─── generate-losers-bracket-round ──────────────────────────────────────────

(deftest losers-round-zero-pairs-wb-losers
  (let [wb     [{:status "complete" :winner-sub "a" :player-one-sub "a" :player-two-sub "x"}
                {:status "complete" :winner-sub "b" :player-one-sub "y" :player-two-sub "b"}
                {:status "complete" :winner-sub "c" :player-one-sub "c" :player-two-sub "z"}
                {:status "complete" :winner-sub "d" :player-one-sub "w" :player-two-sub "d"}]
        result (rules/generate-losers-bracket-round 0 wb nil)]
    (is (= 2 (count result)))
    ;; Losers in order: x, y, z, w
    (is (= "x" (:player-one-sub (first result))))
    (is (= "y" (:player-two-sub (first result))))
    (is (= "z" (:player-one-sub (second result))))
    (is (= "w" (:player-two-sub (second result))))))

(deftest losers-round-zero-ignores-byes
  (let [wb     [{:status "complete" :winner-sub "a" :player-one-sub "a" :player-two-sub nil}
                {:status "complete" :winner-sub "b" :player-one-sub "b" :player-two-sub "y"}]
        result (rules/generate-losers-bracket-round 0 wb nil)]
    (is (= 1 (count result)))
    (is (= "y" (:player-one-sub (first result))))
    (is (nil? (:player-two-sub (first result))))))

(deftest losers-round-major-mixes-lb-winners-and-wb-losers
  (let [wb-round-1 [{:status "complete" :winner-sub "a" :player-one-sub "a" :player-two-sub "x"}
                    {:status "complete" :winner-sub "b" :player-one-sub "b" :player-two-sub "y"}]
        lb-round-0 [{:status "complete" :winner-sub "l1" :player-one-sub "l1" :player-two-sub "l2"}
                    {:status "complete" :winner-sub "l3" :player-one-sub "l3" :player-two-sub "l4"}]
        result     (rules/generate-losers-bracket-round 1 wb-round-1 lb-round-0)]
    (is (= 2 (count result)))
    (is (= "l1" (:player-one-sub (first result))))
    (is (= "x"  (:player-two-sub (first result))))
    (is (= "l3" (:player-one-sub (second result))))
    (is (= "y"  (:player-two-sub (second result))))))

(deftest losers-round-minor-pairs-lb-winners
  (let [lb-prev [{:status "complete" :winner-sub "w1" :player-one-sub "w1" :player-two-sub "a"}
                 {:status "complete" :winner-sub "w2" :player-one-sub "w2" :player-two-sub "b"}]
        result  (rules/generate-losers-bracket-round 2 nil lb-prev)]
    (is (= 1 (count result)))
    (is (= "w1" (:player-one-sub (first result))))
    (is (= "w2" (:player-two-sub (first result))))))

;; ─── winners-source-round-for-losers-round ──────────────────────────────────

(deftest wb-source-for-lb-round
  (is (= 0   (rules/winners-source-round-for-losers-round 0)))
  (is (= 1   (rules/winners-source-round-for-losers-round 1)))
  (is (nil? (rules/winners-source-round-for-losers-round 2)))
  (is (= 2   (rules/winners-source-round-for-losers-round 3)))
  (is (nil? (rules/winners-source-round-for-losers-round 4)))
  (is (= 3   (rules/winners-source-round-for-losers-round 5))))

;; ─── grand-final-pairing ────────────────────────────────────────────────────

(deftest grand-final-single-match
  (let [result (rules/grand-final-pairing "wb-champ" "lb-champ")]
    (is (= 1 (count result)))
    (is (= "wb-champ" (:player-one-sub (first result))))
    (is (= "lb-champ" (:player-two-sub (first result))))))
