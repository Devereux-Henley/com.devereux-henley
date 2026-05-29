(ns com.devereux-henley.rts-domain.handlers.league-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.league :as handlers.league])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def ^:private test-league-eid (UUID/fromString "11111111-1111-1111-1111-111111111111"))
(def ^:private test-game-eid (UUID/fromString "22222222-2222-2222-2222-222222222222"))
(def ^:private test-deps {:datalog-connection nil})

(def ^:private test-league
  {:eid            test-league-eid :game-eid    test-game-eid
   :name           "Test League"   :description "A test league."
   :created-by-sub "dev-admin"     :version     1
   :created-at     (Instant/now)   :updated-at  (Instant/now)})

(deftest get-league-by-eid-tags-type
  (with-redefs [data-access.contract/league-by-eid (fn [_ _] test-league)]
    (let [result (handlers.league/get-league-by-eid test-deps test-league-eid)]
      (is (= :league/league (:type result)))
      (is (= test-league-eid (:eid result))))))

(deftest get-league-by-eid-returns-nil-when-missing
  (with-redefs [data-access.contract/league-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.league/get-league-by-eid test-deps test-league-eid)))))

(deftest get-leagues-for-game-tags-each
  (with-redefs [data-access.contract/leagues-for-game
                (fn [_ _] [test-league (assoc test-league :name "Second")])]
    (let [results (handlers.league/get-leagues-for-game test-deps test-game-eid)]
      (is (= 2 (count results)))
      (is (every? #(= :league/league (:type %)) results)))))

(deftest create-league-tags-result
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/create-league!
                  (fn [_ spec]
                    (reset! captured spec)
                    (merge test-league (select-keys spec [:eid :name :description :created-by-sub])))]
      (let [result (handlers.league/create-league
                    test-deps
                    {:eid            test-league-eid
                     :game-eid       test-game-eid
                     :name           "New League"
                     :description    "Fresh"
                     :created-by-sub "dev-admin"})]
        (is (= :league/league (:type result)))
        (is (= "New League" (:name result)))
        (is (= test-game-eid (:game-eid @captured)))))))
