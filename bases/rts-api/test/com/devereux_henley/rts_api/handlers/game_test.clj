(ns com.devereux-henley.rts-api.handlers.game-test
  (:require
   [clojure.test :refer :all]
   [com.devereux-henley.rts-api.db.game :as db.game]
   [com.devereux-henley.rts-api.handlers.game :as handlers.game])
  (:import
   [java.util UUID]))

(def ^:private test-game-eid (UUID/fromString "eea787d7-1065-45eb-a3f6-e26f32c294a1"))
(def ^:private test-faction-eid (UUID/fromString "35dd38fa-2bcc-4492-8f58-a106d0d02cbb"))
(def ^:private test-deps {:connection nil})

;; --- get-game-by-eid ---

(deftest get-game-by-eid-assigns-type
  (with-redefs [db.game/get-game-by-eid (fn [_ _] {:eid test-game-eid :name "Total War: Warhammer III"})]
    (let [result (handlers.game/get-game-by-eid test-deps test-game-eid)]
      (is (= :game/game (:type result))))))

(deftest get-game-by-eid-preserves-fields
  (with-redefs [db.game/get-game-by-eid (fn [_ _] {:eid test-game-eid :name "Total War: Warhammer III"})]
    (let [result (handlers.game/get-game-by-eid test-deps test-game-eid)]
      (is (= test-game-eid (:eid result)))
      (is (= "Total War: Warhammer III" (:name result))))))

;; --- get-games ---

(deftest get-games-assigns-type-to-each-game
  (with-redefs [db.game/get-games (fn [_] [{:eid test-game-eid :name "Game A"}
                                            {:eid (UUID/randomUUID) :name "Game B"}])]
    (let [results (handlers.game/get-games test-deps)]
      (is (every? #(= :game/game (:type %)) results)))))

(deftest get-games-returns-all-results
  (with-redefs [db.game/get-games (fn [_] [{:eid test-game-eid :name "Game A"}
                                            {:eid (UUID/randomUUID) :name "Game B"}])]
    (is (= 2 (count (handlers.game/get-games test-deps))))))

(deftest get-games-empty-result
  (with-redefs [db.game/get-games (fn [_] [])]
    (is (= [] (handlers.game/get-games test-deps)))))

;; --- get-factions-for-game ---

(deftest get-factions-for-game-assigns-type-to-each-faction
  (with-redefs [db.game/get-factions-for-game (fn [_ _] [{:eid test-faction-eid :name "Empire"}
                                                          {:eid (UUID/randomUUID) :name "Chaos"}])]
    (let [results (handlers.game/get-factions-for-game test-deps test-game-eid)]
      (is (every? #(= :game/faction (:type %)) results)))))

(deftest get-factions-for-game-returns-all-results
  (with-redefs [db.game/get-factions-for-game (fn [_ _] [{:eid test-faction-eid :name "Empire"}
                                                          {:eid (UUID/randomUUID) :name "Chaos"}])]
    (is (= 2 (count (handlers.game/get-factions-for-game test-deps test-game-eid))))))

(deftest get-factions-for-game-empty-result
  (with-redefs [db.game/get-factions-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-factions-for-game test-deps test-game-eid)))))

;; --- get-faction-by-eid ---

(deftest get-faction-by-eid-assigns-type
  (with-redefs [db.game/get-faction-by-eid (fn [_ _] {:eid test-faction-eid :name "Empire"})]
    (let [result (handlers.game/get-faction-by-eid test-deps test-faction-eid)]
      (is (= :game/faction (:type result))))))

(deftest get-faction-by-eid-preserves-fields
  (with-redefs [db.game/get-faction-by-eid (fn [_ _] {:eid test-faction-eid :name "Empire"})]
    (let [result (handlers.game/get-faction-by-eid test-deps test-faction-eid)]
      (is (= test-faction-eid (:eid result)))
      (is (= "Empire" (:name result))))))

;; --- get-socials-for-game ---

(deftest get-socials-for-game-assigns-type-to-each-social
  (with-redefs [db.game/get-socials-for-game (fn [_ _] [{:eid (UUID/randomUUID) :url "https://discord.gg/test"}])]
    (let [results (handlers.game/get-socials-for-game test-deps test-game-eid)]
      (is (every? #(= :game/social (:type %)) results)))))

(deftest get-socials-for-game-empty-result
  (with-redefs [db.game/get-socials-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-socials-for-game test-deps test-game-eid)))))

;; --- get-game-social-link-by-eid ---

(deftest get-game-social-link-by-eid-assigns-type
  (let [link-eid (UUID/randomUUID)]
    (with-redefs [db.game/get-game-social-link-by-eid (fn [_ _] {:eid link-eid :url "https://discord.gg/test"})]
      (let [result (handlers.game/get-game-social-link-by-eid test-deps link-eid)]
        (is (= :game/social (:type result)))))))

(deftest get-game-social-link-by-eid-preserves-fields
  (let [link-eid (UUID/randomUUID)]
    (with-redefs [db.game/get-game-social-link-by-eid (fn [_ _] {:eid link-eid :url "https://discord.gg/test"})]
      (let [result (handlers.game/get-game-social-link-by-eid test-deps link-eid)]
        (is (= link-eid (:eid result)))
        (is (= "https://discord.gg/test" (:url result)))))))

;; --- get-units-for-game ---

(deftest get-units-for-game-assigns-type-to-each-unit
  (with-redefs [db.game/get-units-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Swordsmen"}
                                                       {:eid (UUID/randomUUID) :name "Handgunners"}])]
    (let [results (handlers.game/get-units-for-game test-deps test-game-eid)]
      (is (every? #(= :game/unit (:type %)) results)))))

(deftest get-units-for-game-returns-all-results
  (with-redefs [db.game/get-units-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Swordsmen"}
                                                       {:eid (UUID/randomUUID) :name "Handgunners"}])]
    (is (= 2 (count (handlers.game/get-units-for-game test-deps test-game-eid))))))

(deftest get-units-for-game-empty-result
  (with-redefs [db.game/get-units-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-units-for-game test-deps test-game-eid)))))
