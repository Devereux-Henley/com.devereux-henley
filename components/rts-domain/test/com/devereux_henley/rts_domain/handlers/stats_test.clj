(ns com.devereux-henley.rts-domain.handlers.stats-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.stats :as handlers.stats])
  (:import
   [java.util UUID]))

(def ^:private test-deps {:connection nil})
(def ^:private test-eid (UUID/randomUUID))

(def ^:private rows
  [{:faction-eid    (UUID/randomUUID) :faction-name "Empire"
    :matches-played 3                 :wins         2        :losses 1 :draws 0}
   {:faction-eid    (UUID/randomUUID) :faction-name "Beastmen"
    :matches-played 2                 :wins         1          :losses 1 :draws 0}])

(deftest game-faction-standings-tags-rows
  (with-redefs [data-access.contract/get-faction-standings-for-game (fn [_ _] rows)]
    (let [resp (handlers.stats/get-game-faction-standings test-deps test-eid)]
      (is (= :stats/game-faction-standings (:type resp)))
      (is (= "game" (:scope resp)))
      (is (= test-eid (:scope-eid resp)))
      (is (= 2 (count (:rows resp))))
      (is (every? #(= :stats/faction-row (:type %)) (:rows resp))))))

(deftest league-faction-standings-tags-rows
  (with-redefs [data-access.contract/get-faction-standings-for-league (fn [_ _] rows)]
    (let [resp (handlers.stats/get-league-faction-standings test-deps test-eid)]
      (is (= :stats/league-faction-standings (:type resp)))
      (is (= "league" (:scope resp)))
      (is (every? #(= :stats/faction-row (:type %)) (:rows resp))))))

(deftest season-faction-standings-tags-rows
  (with-redefs [data-access.contract/get-faction-standings-for-season (fn [_ _] rows)]
    (let [resp (handlers.stats/get-season-faction-standings test-deps test-eid)]
      (is (= :stats/season-faction-standings (:type resp)))
      (is (= "season" (:scope resp))))))

(deftest faction-standings-empty-result
  (with-redefs [data-access.contract/get-faction-standings-for-game (fn [_ _] [])]
    (let [resp (handlers.stats/get-game-faction-standings test-deps test-eid)]
      (is (= [] (:rows resp))))))
