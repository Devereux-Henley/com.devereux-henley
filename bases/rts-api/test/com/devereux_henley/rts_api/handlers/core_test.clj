(ns com.devereux-henley.rts-api.handlers.core-test
  (:require
   [clojure.test :refer :all]
   [com.devereux-henley.rts-api.handlers.core :as handlers.core]))

(deftest assign-model-type-assocs-type
  (is (= {:id 1 :name "test" :type :game/game}
         (handlers.core/assign-model-type :game/game {:id 1 :name "test"}))))

(deftest assign-model-type-overwrites-existing-type
  (is (= {:id 1 :type :game/faction}
         (handlers.core/assign-model-type :game/faction {:id 1 :type :game/game}))))

(deftest assign-model-type-nil-model-returns-nil
  (is (nil? (handlers.core/assign-model-type :game/game nil))))

(deftest assign-model-type-preserves-all-fields
  (let [model {:id 42 :eid "abc" :name "foo" :version 1}
        result (handlers.core/assign-model-type :tournament/tournament model)]
    (is (= :tournament/tournament (:type result)))
    (is (= 42 (:id result)))
    (is (= "abc" (:eid result)))
    (is (= "foo" (:name result)))
    (is (= 1 (:version result)))))
