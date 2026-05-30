(ns com.devereux-henley.rts-domain.handlers.game-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.draft :as handlers.draft]
   [com.devereux-henley.rts-domain.handlers.game :as handlers.game])
  (:import
   [java.util UUID]))

(def ^:private test-game-eid (UUID/fromString "eea787d7-1065-45eb-a3f6-e26f32c294a1"))
(def ^:private test-faction-eid (UUID/fromString "35dd38fa-2bcc-4492-8f58-a106d0d02cbb"))
(def ^:private test-game-mode-eid (UUID/fromString "a1b2c3d4-0001-4000-8000-000000000001"))
(def ^:private test-unit-eid (UUID/fromString "0002000b-0000-4000-8000-000000000000"))
(def ^:private test-deps {:datalog-connection nil})

;; --- get-game-by-eid ---

(deftest get-game-by-eid-assigns-type
  (with-redefs [data-access.contract/game-by-eid (fn [_ _] {:eid test-game-eid :name "Total War: Warhammer III"})]
    (let [result (handlers.game/get-game-by-eid test-deps test-game-eid)]
      (is (= :game/game (:type result))))))

(deftest get-game-by-eid-preserves-fields
  (with-redefs [data-access.contract/game-by-eid (fn [_ _] {:eid test-game-eid :name "Total War: Warhammer III"})]
    (let [result (handlers.game/get-game-by-eid test-deps test-game-eid)]
      (is (= test-game-eid (:eid result)))
      (is (= "Total War: Warhammer III" (:name result))))))

(deftest get-game-by-eid-returns-nil-when-not-found
  (with-redefs [data-access.contract/game-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.game/get-game-by-eid test-deps test-game-eid)))))

;; --- get-games ---

(deftest get-games-assigns-type-to-each-game
  (with-redefs [data-access.contract/games (fn [_] [{:eid test-game-eid :name "Game A"}
                                                    {:eid (UUID/randomUUID) :name "Game B"}])]
    (let [results (handlers.game/get-games test-deps)]
      (is (every? #(= :game/game (:type %)) results)))))

(deftest get-games-returns-all-results
  (with-redefs [data-access.contract/games (fn [_] [{:eid test-game-eid :name "Game A"}
                                                    {:eid (UUID/randomUUID) :name "Game B"}])]
    (is (= 2 (count (handlers.game/get-games test-deps))))))

(deftest get-games-empty-result
  (with-redefs [data-access.contract/games (fn [_] [])]
    (is (= [] (handlers.game/get-games test-deps)))))

;; --- get-factions-for-game ---

(deftest get-factions-for-game-assigns-type-to-each-faction
  (with-redefs [data-access.contract/factions-for-game (fn [_ _] [{:eid test-faction-eid :name "Empire"}
                                                                  {:eid (UUID/randomUUID) :name "Chaos"}])]
    (let [results (handlers.game/get-factions-for-game test-deps test-game-eid)]
      (is (every? #(= :game/faction (:type %)) results)))))

(deftest get-factions-for-game-returns-all-results
  (with-redefs [data-access.contract/factions-for-game (fn [_ _] [{:eid test-faction-eid :name "Empire"}
                                                                  {:eid (UUID/randomUUID) :name "Chaos"}])]
    (is (= 2 (count (handlers.game/get-factions-for-game test-deps test-game-eid))))))

(deftest get-factions-for-game-empty-result
  (with-redefs [data-access.contract/factions-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-factions-for-game test-deps test-game-eid)))))

;; --- get-faction-by-eid ---

(deftest get-faction-by-eid-assigns-type
  (with-redefs [data-access.contract/faction-by-eid (fn [_ _] {:eid test-faction-eid :name "Empire"})]
    (let [result (handlers.game/get-faction-by-eid test-deps test-faction-eid)]
      (is (= :game/faction (:type result))))))

(deftest get-faction-by-eid-preserves-fields
  (with-redefs [data-access.contract/faction-by-eid (fn [_ _] {:eid test-faction-eid :name "Empire"})]
    (let [result (handlers.game/get-faction-by-eid test-deps test-faction-eid)]
      (is (= test-faction-eid (:eid result)))
      (is (= "Empire" (:name result))))))

(deftest get-faction-by-eid-returns-nil-when-not-found
  (with-redefs [data-access.contract/faction-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.game/get-faction-by-eid test-deps test-faction-eid)))))

;; --- get-socials-for-game ---

(deftest get-socials-for-game-assigns-type-to-each-social
  (with-redefs [data-access.contract/socials-for-game (fn [_ _] [{:eid (UUID/randomUUID) :url "https://discord.gg/test"}])]
    (let [results (handlers.game/get-socials-for-game test-deps test-game-eid)]
      (is (every? #(= :game/social (:type %)) results)))))

(deftest get-socials-for-game-empty-result
  (with-redefs [data-access.contract/socials-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-socials-for-game test-deps test-game-eid)))))

;; --- get-unit-by-eid ---

(deftest get-unit-by-eid-assigns-type
  (let [unit-eid (UUID/fromString "c1000000-0000-0000-0000-000000000001")]
    (with-redefs [data-access.contract/unit-by-eid (fn [_ _] {:eid unit-eid :name "Karl Franz"})]
      (let [result (handlers.game/get-unit-by-eid test-deps unit-eid)]
        (is (= :game/unit (:type result)))))))

(deftest get-unit-by-eid-preserves-fields
  (let [unit-eid (UUID/fromString "c1000000-0000-0000-0000-000000000001")]
    (with-redefs [data-access.contract/unit-by-eid (fn [_ _] {:eid unit-eid :name "Karl Franz" :description "Emperor of the Empire"})]
      (let [result (handlers.game/get-unit-by-eid test-deps unit-eid)]
        (is (= unit-eid (:eid result)))
        (is (= "Karl Franz" (:name result)))
        (is (= "Emperor of the Empire" (:description result)))))))

(deftest get-unit-by-eid-returns-nil-when-not-found
  (let [unit-eid (UUID/fromString "c1000000-0000-0000-0000-000000000001")]
    (with-redefs [data-access.contract/unit-by-eid (fn [_ _] nil)]
      (is (nil? (handlers.game/get-unit-by-eid test-deps unit-eid))))))

;; --- get-units-for-game ---

(deftest get-units-for-game-assigns-type-to-each-unit
  (with-redefs [data-access.contract/units-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Swordsmen"}
                                                               {:eid (UUID/randomUUID) :name "Handgunners"}])]
    (let [results (handlers.game/get-units-for-game test-deps test-game-eid)]
      (is (every? #(= :game/unit (:type %)) results)))))

(deftest get-units-for-game-returns-all-results
  (with-redefs [data-access.contract/units-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Swordsmen"}
                                                               {:eid (UUID/randomUUID) :name "Handgunners"}])]
    (is (= 2 (count (handlers.game/get-units-for-game test-deps test-game-eid))))))

(deftest get-units-for-game-empty-result
  (with-redefs [data-access.contract/units-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-units-for-game test-deps test-game-eid)))))

;; --- get-units-for-faction ---

(deftest get-units-for-faction-assigns-type-to-each-unit
  (with-redefs [data-access.contract/units-for-faction (fn [_ _] [{:eid (UUID/randomUUID) :name "Swordsmen"}
                                                                  {:eid (UUID/randomUUID) :name "Handgunners"}])]
    (let [results (handlers.game/get-units-for-faction test-deps test-faction-eid)]
      (is (every? #(= :game/unit (:type %)) results)))))

(deftest get-units-for-faction-returns-all-results
  (with-redefs [data-access.contract/units-for-faction (fn [_ _] [{:eid (UUID/randomUUID) :name "Swordsmen"}
                                                                  {:eid (UUID/randomUUID) :name "Handgunners"}])]
    (is (= 2 (count (handlers.game/get-units-for-faction test-deps test-faction-eid))))))

(deftest get-units-for-faction-empty-result
  (with-redefs [data-access.contract/units-for-faction (fn [_ _] [])]
    (is (= [] (handlers.game/get-units-for-faction test-deps test-faction-eid)))))

;; --- get-game-mode-by-eid ---

(deftest get-game-mode-by-eid-assigns-type
  (with-redefs [data-access.contract/game-mode-by-eid (fn [_ _] {:eid test-game-mode-eid :name "Land Battle"})]
    (let [result (handlers.game/get-game-mode-by-eid test-deps test-game-mode-eid)]
      (is (= :game/game-mode (:type result))))))

(deftest get-game-mode-by-eid-preserves-fields
  (with-redefs [data-access.contract/game-mode-by-eid (fn [_ _] {:eid test-game-mode-eid :name "Land Battle" :draft-value 1 :player-count 2 :reinforcement-value 0 :reinforcements-enabled true})]
    (let [result (handlers.game/get-game-mode-by-eid test-deps test-game-mode-eid)]
      (is (= test-game-mode-eid (:eid result)))
      (is (= "Land Battle" (:name result)))
      (is (= 1 (:draft-value result)))
      (is (= 2 (:player-count result)))
      (is (= 0 (:reinforcement-value result)))
      (is (true? (:reinforcements-enabled result))))))

(deftest get-game-mode-by-eid-returns-nil-when-not-found
  (with-redefs [data-access.contract/game-mode-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.game/get-game-mode-by-eid test-deps test-game-mode-eid)))))

;; --- get-game-modes-for-game ---

(deftest get-game-modes-for-game-assigns-type-to-each-mode
  (with-redefs [data-access.contract/game-modes-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Land Battle"}
                                                                    {:eid (UUID/randomUUID) :name "Domination"}])]
    (let [results (handlers.game/get-game-modes-for-game test-deps test-game-eid)]
      (is (every? #(= :game/game-mode (:type %)) results)))))

(deftest get-game-modes-for-game-returns-all-results
  (with-redefs [data-access.contract/game-modes-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Land Battle"}
                                                                    {:eid (UUID/randomUUID) :name "Domination"}])]
    (is (= 2 (count (handlers.game/get-game-modes-for-game test-deps test-game-eid))))))

(deftest get-game-modes-for-game-empty-result
  (with-redefs [data-access.contract/game-modes-for-game (fn [_ _] [])]
    (is (= [] (handlers.game/get-game-modes-for-game test-deps test-game-eid)))))

;; --- faction-view-model ---

(deftest faction-view-model-nests-entity-under-data-and-embeds-units
  (with-redefs [data-access.contract/faction-detail
                (fn [_ _] {:eid   test-faction-eid                               :name "Empire" :description "d"
                           :units [{:unit-category-name "Lord" :name "General"}
                                   {:unit-category-name "Lord" :name "Wizard"}
                                   {:unit-category-name "Hero" :name "Captain"}]})]
    (let [result (handlers.game/faction-view-model test-deps test-faction-eid)
          cats   (get-in result [:data :_embedded :units-by-category])]
      (is (= "Empire" (get-in result [:data :name])))
      (is (nil? (get-in result [:data :units])) "raw :units list is consumed into the grouping")
      (is (= ["Lord" "Hero"] (mapv :category cats)))
      (is (= 2 (count (:units (first cats))))))))

(deftest faction-view-model-returns-missing-marker-when-absent
  (with-redefs [data-access.contract/faction-detail (fn [_ _] nil)]
    (is (= :missing/resource (:type (handlers.game/faction-view-model test-deps test-faction-eid))))))

;; --- unit-view-model ---

(deftest unit-view-model-nests-entity-under-data-and-lifts-lists
  (with-redefs [data-access.contract/unit-detail
                (fn [_ _] {:eid              test-unit-eid                                                     :name  "Bray-Shaman" :description "d" :unit-statistics {}
                           :abilities        [{:eid (UUID/randomUUID) :name "Shadow" :description "x"}]
                           :draftable-spells [{:eid (UUID/randomUUID) :name "Fireball" :mana-cost 5 :cost 10}]
                           :mounts           []                                                                :items []})
                handlers.draft/parse-unit-statistics (fn [_] {:stats [{:label "Health" :value 100}]})]
    (let [result (handlers.game/unit-view-model test-deps test-unit-eid)]
      (is (= "Bray-Shaman" (get-in result [:data :name])))
      (is (nil? (get-in result [:data :abilities])) "resolved lists are lifted out of :data")
      (is (= 1 (count (:abilities result))))
      (is (= 1 (count (:draftable-spells result))))
      (is (= [{:label "Health" :value 100}] (:unit-statistics result)))
      (is (nil? (:mounts result)) "empty lists collapse to nil via not-empty")
      (is (nil? (:items result))))))

(deftest unit-view-model-returns-missing-marker-when-absent
  (with-redefs [data-access.contract/unit-detail (fn [_ _] nil)]
    (is (= :missing/resource (:type (handlers.game/unit-view-model test-deps test-unit-eid))))))
