(ns com.devereux-henley.rts-api.domain.game-test
  (:require
   [clojure.test :refer :all]
   [com.devereux-henley.rts-api.domain.game :as domain.game]
   [malli.core :as malli])
  (:import
   [java.util UUID]))

;; --- game ---

(deftest game-valid-input
  (is (malli/validate domain.game/game {:name "Total War: Warhammer III"})))

(deftest game-missing-name
  (is (not (malli/validate domain.game/game {}))))

(deftest game-name-wrong-type
  (is (not (malli/validate domain.game/game {:name 42}))))

(deftest game-name-nil
  (is (not (malli/validate domain.game/game {:name nil}))))

;; --- create-game-specification ---

(deftest create-game-specification-valid-input
  (is (malli/validate domain.game/create-game-specification
                      {:id   (UUID/randomUUID)
                       :name "Total War: Warhammer III"})))

(deftest create-game-specification-missing-id
  (is (not (malli/validate domain.game/create-game-specification
                           {:name "Total War: Warhammer III"}))))

(deftest create-game-specification-id-wrong-type
  (is (not (malli/validate domain.game/create-game-specification
                           {:id   "not-a-uuid"
                            :name "Total War: Warhammer III"}))))

(deftest create-game-specification-missing-name
  (is (not (malli/validate domain.game/create-game-specification
                           {:id (UUID/randomUUID)}))))

(deftest create-game-specification-name-wrong-type
  (is (not (malli/validate domain.game/create-game-specification
                           {:id   (UUID/randomUUID)
                            :name 42}))))

;; --- update-game-specification ---

(deftest update-game-specification-valid-input
  (is (malli/validate domain.game/update-game-specification
                      {:name "Updated Game Name"})))

(deftest update-game-specification-missing-name
  (is (not (malli/validate domain.game/update-game-specification {}))))

(deftest update-game-specification-name-wrong-type
  (is (not (malli/validate domain.game/update-game-specification
                           {:name false}))))

(deftest update-game-specification-name-nil
  (is (not (malli/validate domain.game/update-game-specification
                           {:name nil}))))
