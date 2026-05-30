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

;; --- league-view-model ---

(def ^:private test-season-eid (UUID/fromString "33333333-3333-3333-3333-333333333333"))

(defn- with-league-detail-stubs [f]
  (with-redefs [data-access.contract/league-by-eid
                (fn [_ _] {:eid      test-league-eid :name           "Spring" :description "d"
                           :game-eid test-game-eid   :created-by-sub "owner"})
                data-access.contract/seasons-for-league
                (fn [_ _] [{:eid test-season-eid :ordinal 1 :name nil}])
                data-access.contract/tournaments-for-game
                (fn [_ _] [{:eid    (UUID/randomUUID) :league-eid test-league-eid :season-eid test-season-eid
                            :status "active"          :name       "In League"}
                           {:eid (UUID/randomUUID) :league-eid (UUID/randomUUID) :name "Other League"}])
                data-access.contract/get-faction-standings-for-league
                (fn [_ _] [{:faction-name "Empire" :wins 1 :losses 0 :matches-played 1}])]
    (f)))

(deftest league-view-model-shapes-data-seasons-tournaments-and-standings
  (with-league-detail-stubs
    (fn []
      (let [result (handlers.league/league-view-model test-deps test-league-eid "owner")]
        (is (= "Spring" (get-in result [:data :name])))
        (is (= 1 (count (:tournaments result))) "only tournaments scoped to this league are kept")
        (is (= "Season 1" (-> result :tournaments first :season-display-name)))
        (is (= "Season 1" (-> result :seasons first :display-name)))
        (is (= 1 (count (:rows (:standings result)))))))))

(deftest league-view-model-is-organizer-reflects-viewer
  (with-league-detail-stubs
    (fn []
      (is (true? (:is-organizer (handlers.league/league-view-model test-deps test-league-eid "owner"))))
      (is (false? (:is-organizer (handlers.league/league-view-model test-deps test-league-eid "someone-else")))))))

(deftest league-view-model-returns-missing-marker-when-absent
  (with-redefs [data-access.contract/league-by-eid (fn [_ _] nil)]
    (is (= :missing/resource (:type (handlers.league/league-view-model test-deps test-league-eid "owner"))))))
