(ns com.devereux-henley.rts-domain.handlers.dispute-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.dispute :as handlers.dispute])
  (:import
   [java.util UUID]))

(def ^:private test-deps {:datalog-connection nil})
(def ^:private tournament-eid (UUID/fromString "11111111-1111-1111-1111-111111111111"))
(def ^:private match-eid (UUID/fromString "22222222-2222-2222-2222-222222222222"))
(def ^:private game-eid (UUID/fromString "33333333-3333-3333-3333-333333333333"))
(def ^:private dispute-eid (UUID/fromString "44444444-4444-4444-4444-444444444444"))

(def ^:private test-match
  {:eid match-eid :tournament-eid tournament-eid :status "complete"})

(def ^:private valid-spec
  {:tournament-eid tournament-eid
   :match-eid      match-eid
   :kind           "conflicting-result"
   :reporter-sub   "dev-player-one"
   :detail         "results disagree on game 4"})

;; ─── tagging ────────────────────────────────────────────────────────────────

(deftest get-dispute-by-eid-tags-result
  (with-redefs [data-access.contract/dispute-by-eid
                (fn [_ _] {:eid      dispute-eid :status       "open"           :kind "missing-replay"
                           :priority "low"       :reporter-sub "dev-player-two"})]
    (let [d (handlers.dispute/get-dispute-by-eid test-deps dispute-eid)]
      (is (= :dispute/dispute (:type d)))
      (is (= dispute-eid (:eid d))))))

(deftest get-dispute-by-eid-returns-nil-when-absent
  (with-redefs [data-access.contract/dispute-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.dispute/get-dispute-by-eid test-deps dispute-eid)))))

(deftest get-open-disputes-tags-each-row
  (with-redefs [data-access.contract/open-disputes-for-tournament
                (fn [_ _] [{:eid (UUID/randomUUID) :status "open"}
                           {:eid (UUID/randomUUID) :status "open"}])]
    (let [rows (handlers.dispute/get-open-disputes-for-tournament test-deps tournament-eid)]
      (is (= 2 (count rows)))
      (is (every? #(= :dispute/dispute (:type %)) rows)))))

(deftest get-open-dispute-count-passes-through
  (with-redefs [data-access.contract/open-dispute-count-for-tournament (fn [_ _] 5)]
    (is (= 5 (handlers.dispute/get-open-dispute-count-for-tournament test-deps tournament-eid)))))

;; ─── open-dispute happy path + validation ────────────────────────────────────

(deftest open-dispute-creates-and-tags
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/match-by-eid    (fn [_ _] test-match)
                  data-access.contract/games-for-match (fn [_ _] [])
                  data-access.contract/create-dispute! (fn [_ spec]
                                                         (reset! captured spec)
                                                         (assoc spec :eid dispute-eid :status "open"))]
      (let [result (handlers.dispute/open-dispute test-deps valid-spec)]
        (is (= :dispute/dispute (:type result)))
        (is (= "conflicting-result" (:kind @captured)))))))

(deftest open-dispute-rejects-missing-match
  (with-redefs [data-access.contract/match-by-eid (fn [_ _] nil)]
    (is (= :dispute/error (:type (handlers.dispute/open-dispute test-deps valid-spec))))))

(deftest open-dispute-rejects-match-from-other-tournament
  (with-redefs [data-access.contract/match-by-eid
                (fn [_ _] (assoc test-match :tournament-eid (UUID/randomUUID)))]
    (is (= :dispute/error (:type (handlers.dispute/open-dispute test-deps valid-spec))))))

(deftest open-dispute-rejects-game-not-in-match
  (with-redefs [data-access.contract/match-by-eid    (fn [_ _] test-match)
                data-access.contract/games-for-match (fn [_ _] [{:eid (UUID/randomUUID)}])]
    (let [spec   (assoc valid-spec :match-game-eid game-eid)
          result (handlers.dispute/open-dispute test-deps spec)]
      (is (= :dispute/error (:type result))))))

(deftest open-dispute-accepts-game-belonging-to-match
  (with-redefs [data-access.contract/match-by-eid    (fn [_ _] test-match)
                data-access.contract/games-for-match (fn [_ _] [{:eid game-eid}])
                data-access.contract/create-dispute! (fn [_ spec] (assoc spec :eid dispute-eid :status "open"))]
    (let [spec   (assoc valid-spec :match-game-eid game-eid)
          result (handlers.dispute/open-dispute test-deps spec)]
      (is (= :dispute/dispute (:type result))))))

;; ─── resolve / dismiss lifecycle ──────────────────────────────────────────────

(deftest resolve-dispute-marks-open-dispute-resolved
  (with-redefs [data-access.contract/dispute-by-eid   (fn [_ _] {:eid dispute-eid :status "open"})
                data-access.contract/resolve-dispute! (fn [_ eid] {:eid eid :status "resolved"})]
    (let [result (handlers.dispute/resolve-dispute test-deps dispute-eid)]
      (is (= :dispute/dispute (:type result)))
      (is (= "resolved" (:status result))))))

(deftest dismiss-dispute-marks-open-dispute-dismissed
  (with-redefs [data-access.contract/dispute-by-eid   (fn [_ _] {:eid dispute-eid :status "open"})
                data-access.contract/dismiss-dispute! (fn [_ eid] {:eid eid :status "dismissed"})]
    (let [result (handlers.dispute/dismiss-dispute test-deps dispute-eid)]
      (is (= :dispute/dispute (:type result)))
      (is (= "dismissed" (:status result))))))

(deftest resolve-dispute-rejects-missing-dispute
  (with-redefs [data-access.contract/dispute-by-eid (fn [_ _] nil)]
    (is (= :dispute/error (:type (handlers.dispute/resolve-dispute test-deps dispute-eid))))))

(deftest resolve-dispute-rejects-already-closed-dispute
  (testing "a non-open dispute cannot be resolved again"
    (with-redefs [data-access.contract/dispute-by-eid (fn [_ _] {:eid dispute-eid :status "resolved"})]
      (is (= :dispute/error (:type (handlers.dispute/resolve-dispute test-deps dispute-eid)))))))

(deftest dismiss-dispute-rejects-already-closed-dispute
  (with-redefs [data-access.contract/dispute-by-eid (fn [_ _] {:eid dispute-eid :status "dismissed"})]
    (is (= :dispute/error (:type (handlers.dispute/dismiss-dispute test-deps dispute-eid))))))
