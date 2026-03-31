(ns com.devereux-henley.rts-domain.handlers.game-test
  (:require
   [clojure.test :refer :all]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.game :as handlers.game])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def ^:private test-game-eid (UUID/fromString "eea787d7-1065-45eb-a3f6-e26f32c294a1"))
(def ^:private test-faction-eid (UUID/fromString "35dd38fa-2bcc-4492-8f58-a106d0d02cbb"))
(def ^:private test-game-mode-eid (UUID/fromString "a1b2c3d4-0001-4000-8000-000000000001"))
(def ^:private test-deps {:connection nil})

;; --- get-game-by-eid ---

(deftest get-game-by-eid-assigns-type
  (with-redefs [data-access.contract/get-game-by-eid (fn [_ _] {:eid test-game-eid :name "Total War: Warhammer III"})]
    (let [result (handlers.game/get-game-by-eid test-deps test-game-eid)]
      (is (= :game/game (:type result))))))

(deftest get-game-by-eid-preserves-fields
  (with-redefs [data-access.contract/get-game-by-eid (fn [_ _] {:eid test-game-eid :name "Total War: Warhammer III"})]
    (let [result (handlers.game/get-game-by-eid test-deps test-game-eid)]
      (is (= test-game-eid (:eid result)))
      (is (= "Total War: Warhammer III" (:name result))))))

;; --- get-games ---

(deftest get-games-assigns-type-to-each-game
  (with-redefs [data-access.contract/get-games (fn [_] [{:eid test-game-eid :name "Game A"}
                                            {:eid (UUID/randomUUID) :name "Game B"}])]
    (let [results (handlers.game/get-games test-deps)]
      (is (every? #(= :game/game (:type %)) results)))))

(deftest get-games-returns-all-results
  (with-redefs [data-access.contract/get-games (fn [_] [{:eid test-game-eid :name "Game A"}
                                            {:eid (UUID/randomUUID) :name "Game B"}])]
    (is (= 2 (count (handlers.game/get-games test-deps))))))

(deftest get-games-empty-result
  (with-redefs [data-access.contract/get-games (fn [_] [])]
    (is (= [] (handlers.game/get-games test-deps)))))

;; --- get-factions-for-game ---

(deftest get-factions-for-game-assigns-type-to-each-faction
  (with-redefs [data-access.contract/get-factions-for-game (fn [_ _] [{:eid test-faction-eid :name "Empire"}
                                                          {:eid (UUID/randomUUID) :name "Chaos"}])]
    (let [results (handlers.game/get-factions-for-game test-deps test-game-eid)]
      (is (every? #(= :game/faction (:type %)) results)))))

(deftest get-factions-for-game-returns-all-results
  (with-redefs [data-access.contract/get-factions-for-game (fn [_ _] [{:eid test-faction-eid :name "Empire"}
                                                          {:eid (UUID/randomUUID) :name "Chaos"}])]
    (is (= 2 (count (handlers.game/get-factions-for-game test-deps test-game-eid))))))

(deftest get-factions-for-game-empty-result
  (with-redefs [data-access.contract/get-factions-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-factions-for-game test-deps test-game-eid)))))

;; --- get-faction-by-eid ---

(deftest get-faction-by-eid-assigns-type
  (with-redefs [data-access.contract/get-faction-by-eid (fn [_ _] {:eid test-faction-eid :name "Empire"})]
    (let [result (handlers.game/get-faction-by-eid test-deps test-faction-eid)]
      (is (= :game/faction (:type result))))))

(deftest get-faction-by-eid-preserves-fields
  (with-redefs [data-access.contract/get-faction-by-eid (fn [_ _] {:eid test-faction-eid :name "Empire"})]
    (let [result (handlers.game/get-faction-by-eid test-deps test-faction-eid)]
      (is (= test-faction-eid (:eid result)))
      (is (= "Empire" (:name result))))))

;; --- get-socials-for-game ---

(deftest get-socials-for-game-assigns-type-to-each-social
  (with-redefs [data-access.contract/get-socials-for-game (fn [_ _] [{:eid (UUID/randomUUID) :url "https://discord.gg/test"}])]
    (let [results (handlers.game/get-socials-for-game test-deps test-game-eid)]
      (is (every? #(= :game/social (:type %)) results)))))

(deftest get-socials-for-game-empty-result
  (with-redefs [data-access.contract/get-socials-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-socials-for-game test-deps test-game-eid)))))

;; --- get-game-social-link-by-eid ---

(deftest get-game-social-link-by-eid-assigns-type
  (let [link-eid (UUID/randomUUID)]
    (with-redefs [data-access.contract/get-game-social-link-by-eid (fn [_ _] {:eid link-eid :url "https://discord.gg/test"})]
      (let [result (handlers.game/get-game-social-link-by-eid test-deps link-eid)]
        (is (= :game/social (:type result)))))))

(deftest get-game-social-link-by-eid-preserves-fields
  (let [link-eid (UUID/randomUUID)]
    (with-redefs [data-access.contract/get-game-social-link-by-eid (fn [_ _] {:eid link-eid :url "https://discord.gg/test"})]
      (let [result (handlers.game/get-game-social-link-by-eid test-deps link-eid)]
        (is (= link-eid (:eid result)))
        (is (= "https://discord.gg/test" (:url result)))))))

;; --- get-unit-by-eid ---

(deftest get-unit-by-eid-assigns-type
  (let [unit-eid (UUID/fromString "c1000000-0000-0000-0000-000000000001")]
    (with-redefs [data-access.contract/get-unit-by-eid (fn [_ _] {:eid unit-eid :name "Karl Franz"})]
      (let [result (handlers.game/get-unit-by-eid test-deps unit-eid)]
        (is (= :game/unit (:type result)))))))

(deftest get-unit-by-eid-preserves-fields
  (let [unit-eid (UUID/fromString "c1000000-0000-0000-0000-000000000001")]
    (with-redefs [data-access.contract/get-unit-by-eid (fn [_ _] {:eid unit-eid :name "Karl Franz" :description "Emperor of the Empire"})]
      (let [result (handlers.game/get-unit-by-eid test-deps unit-eid)]
        (is (= unit-eid (:eid result)))
        (is (= "Karl Franz" (:name result)))
        (is (= "Emperor of the Empire" (:description result)))))))

;; --- get-units-for-game ---

(deftest get-units-for-game-assigns-type-to-each-unit
  (with-redefs [data-access.contract/get-units-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Swordsmen"}
                                                       {:eid (UUID/randomUUID) :name "Handgunners"}])]
    (let [results (handlers.game/get-units-for-game test-deps test-game-eid)]
      (is (every? #(= :game/unit (:type %)) results)))))

(deftest get-units-for-game-returns-all-results
  (with-redefs [data-access.contract/get-units-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Swordsmen"}
                                                       {:eid (UUID/randomUUID) :name "Handgunners"}])]
    (is (= 2 (count (handlers.game/get-units-for-game test-deps test-game-eid))))))

(deftest get-units-for-game-empty-result
  (with-redefs [data-access.contract/get-units-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-units-for-game test-deps test-game-eid)))))

;; --- get-game-mode-by-eid ---

(deftest get-game-mode-by-eid-assigns-type
  (let [mode-eid (UUID/fromString "a1b2c3d4-0001-4000-8000-000000000001")]
    (with-redefs [data-access.contract/get-game-mode-by-eid (fn [_ _] {:eid mode-eid :name "Land Battle"})]
      (let [result (handlers.game/get-game-mode-by-eid test-deps mode-eid)]
        (is (= :game/game-mode (:type result)))))))

(deftest get-game-mode-by-eid-preserves-fields
  (let [mode-eid (UUID/fromString "a1b2c3d4-0001-4000-8000-000000000001")]
    (with-redefs [data-access.contract/get-game-mode-by-eid (fn [_ _] {:eid mode-eid :name "Land Battle" :draft-value 1 :player-count 2 :reinforcement-value 0 :reinforcements-enabled 1})]
      (let [result (handlers.game/get-game-mode-by-eid test-deps mode-eid)]
        (is (= mode-eid (:eid result)))
        (is (= "Land Battle" (:name result)))
        (is (= 1 (:draft-value result)))
        (is (= 2 (:player-count result)))
        (is (= 0 (:reinforcement-value result)))
        (is (= 1 (:reinforcements-enabled result)))))))

;; --- get-game-modes-for-game ---

(deftest get-game-modes-for-game-assigns-type-to-each-mode
  (with-redefs [data-access.contract/get-game-modes-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Land Battle"}
                                                                         {:eid (UUID/randomUUID) :name "Domination"}])]
    (let [results (handlers.game/get-game-modes-for-game test-deps test-game-eid)]
      (is (every? #(= :game/game-mode (:type %)) results)))))

(deftest get-game-modes-for-game-returns-all-results
  (with-redefs [data-access.contract/get-game-modes-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Land Battle"}
                                                                         {:eid (UUID/randomUUID) :name "Domination"}])]
    (is (= 2 (count (handlers.game/get-game-modes-for-game test-deps test-game-eid))))))

(deftest get-game-modes-for-game-empty-result
  (with-redefs [data-access.contract/get-game-modes-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-game-modes-for-game test-deps test-game-eid)))))

;; --- get-draft-by-eid ---

(def ^:private test-player-sub "auth0|test-player-sub")

(deftest get-draft-by-eid-assigns-type
  (let [draft-eid (UUID/randomUUID)]
    (with-redefs [data-access.contract/get-draft-by-eid (fn [_ _] {:eid draft-eid :game-mode-eid test-game-mode-eid :faction-eid test-faction-eid :player-sub test-player-sub})]
      (let [result (handlers.game/get-draft-by-eid test-deps draft-eid)]
        (is (= :game/draft (:type result)))))))

(deftest get-draft-by-eid-preserves-fields
  (let [draft-eid (UUID/randomUUID)]
    (with-redefs [data-access.contract/get-draft-by-eid (fn [_ _] {:eid draft-eid :game-mode-eid test-game-mode-eid :faction-eid test-faction-eid :player-sub test-player-sub})]
      (let [result (handlers.game/get-draft-by-eid test-deps draft-eid)]
        (is (= draft-eid (:eid result)))
        (is (= test-game-mode-eid (:game-mode-eid result)))
        (is (= test-faction-eid (:faction-eid result)))
        (is (= test-player-sub (:player-sub result)))))))

;; --- get-drafts-for-player ---

(deftest get-drafts-for-player-assigns-type-to-each-draft
  (with-redefs [data-access.contract/get-drafts-for-player (fn [_ _] [{:eid (UUID/randomUUID) :game-mode-eid test-game-mode-eid :faction-eid test-faction-eid :player-sub test-player-sub}
                                                                        {:eid (UUID/randomUUID) :game-mode-eid (UUID/randomUUID) :faction-eid (UUID/randomUUID) :player-sub test-player-sub}])]
    (let [results (handlers.game/get-drafts-for-player test-deps test-player-sub)]
      (is (every? #(= :game/draft (:type %)) results)))))

(deftest get-drafts-for-player-returns-all-results
  (with-redefs [data-access.contract/get-drafts-for-player (fn [_ _] [{:eid (UUID/randomUUID) :game-mode-eid test-game-mode-eid :faction-eid test-faction-eid :player-sub test-player-sub}
                                                                        {:eid (UUID/randomUUID) :game-mode-eid (UUID/randomUUID) :faction-eid (UUID/randomUUID) :player-sub test-player-sub}])]
    (is (= 2 (count (handlers.game/get-drafts-for-player test-deps test-player-sub))))))

(deftest get-drafts-for-player-empty-result
  (with-redefs [data-access.contract/get-drafts-for-player (fn [_ _] [])]
    (is (= [] (handlers.game/get-drafts-for-player test-deps test-player-sub)))))

;; --- create-draft ---

(deftest create-draft-assigns-type
  (with-redefs [data-access.contract/create-draft (fn [_ spec] spec)]
    (let [result (handlers.game/create-draft test-deps {:eid (UUID/randomUUID) :game-mode-eid test-game-mode-eid :faction-eid test-faction-eid :player-sub test-player-sub})]
      (is (= :game/draft (:type result))))))

(deftest create-draft-injects-created-at-timestamp
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/create-draft (fn [_ spec] (reset! captured spec) spec)]
      (handlers.game/create-draft test-deps {:eid (UUID/randomUUID) :game-mode-eid test-game-mode-eid :faction-eid test-faction-eid :player-sub test-player-sub})
      (is (instance? Instant (:created-at @captured))))))

(deftest create-draft-injects-updated-at-timestamp
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/create-draft (fn [_ spec] (reset! captured spec) spec)]
      (handlers.game/create-draft test-deps {:eid (UUID/randomUUID) :game-mode-eid test-game-mode-eid :faction-eid test-faction-eid :player-sub test-player-sub})
      (is (instance? Instant (:updated-at @captured))))))

(deftest create-draft-created-at-equals-updated-at
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/create-draft (fn [_ spec] (reset! captured spec) spec)]
      (handlers.game/create-draft test-deps {:eid (UUID/randomUUID) :game-mode-eid test-game-mode-eid :faction-eid test-faction-eid :player-sub test-player-sub})
      (is (= (:created-at @captured) (:updated-at @captured))))))

(deftest create-draft-preserves-specification-fields
  (let [captured  (atom nil)
        draft-eid (UUID/randomUUID)
        spec      {:eid draft-eid :game-mode-eid test-game-mode-eid :faction-eid test-faction-eid :player-sub test-player-sub}]
    (with-redefs [data-access.contract/create-draft (fn [_ s] (reset! captured s) s)]
      (handlers.game/create-draft test-deps spec)
      (is (= draft-eid (:eid @captured)))
      (is (= test-game-mode-eid (:game-mode-eid @captured)))
      (is (= test-faction-eid (:faction-eid @captured)))
      (is (= test-player-sub (:player-sub @captured))))))
