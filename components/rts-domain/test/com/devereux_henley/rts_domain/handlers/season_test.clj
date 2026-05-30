(ns com.devereux-henley.rts-domain.handlers.season-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.season :as handlers.season])
  (:import
   [java.time Instant LocalDateTime ZoneId]
   [java.util UUID]))

(def ^:private test-league-eid (UUID/fromString "11111111-1111-1111-1111-111111111111"))
(def ^:private test-deps {:datalog-connection nil})

(def ^:private test-league
  {:eid            test-league-eid :game-eid    (UUID/randomUUID)
   :name           "L"             :description "d"
   :created-by-sub "dev-admin"     :version     1
   :created-at     (Instant/now)   :updated-at  (Instant/now)})

(def ^:private valid-spec
  {:eid        (UUID/randomUUID)
   :league-eid test-league-eid
   :timezone   (ZoneId/of "UTC")
   :start-at   (LocalDateTime/parse "2026-04-01T00:00:00")
   :end-at     (LocalDateTime/parse "2026-06-30T23:59:00")})

;; ─── tagging / display-name ─────────────────────────────────────────────────

(deftest get-season-by-eid-tags-and-derives-display-name
  (with-redefs [data-access.contract/season-by-eid
                (fn [_ _] {:eid        (UUID/randomUUID) :league-eid test-league-eid
                           :ordinal    3
                           :start-at   (Instant/now)     :end-at     (Instant/now)
                           :version    1                 :created-at (Instant/now)
                           :updated-at (Instant/now)})]
    (let [s (handlers.season/get-season-by-eid test-deps (UUID/randomUUID))]
      (is (= :season/season (:type s)))
      (is (= "Season 3" (:display-name s))))))

(deftest get-season-display-name-uses-override
  (with-redefs [data-access.contract/season-by-eid
                (fn [_ _] {:eid        (UUID/randomUUID) :league-eid test-league-eid
                           :ordinal    1                 :name       "Spring 2026"
                           :start-at   (Instant/now)     :end-at     (Instant/now)
                           :version    1                 :created-at (Instant/now)
                           :updated-at (Instant/now)})]
    (is (= "Spring 2026" (:display-name (handlers.season/get-season-by-eid test-deps (UUID/randomUUID)))))))

;; ─── ordinal auto-assignment ────────────────────────────────────────────────

(deftest create-season-uses-ordinal-1-when-no-prior-seasons
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/league-by-eid          (fn [_ _] test-league)
                  data-access.contract/max-ordinal-for-league (fn [_ _] {:max-ordinal 0})
                  data-access.contract/create-season!         (fn [_ spec]
                                                                (reset! captured spec)
                                                                spec)]
      (handlers.season/create-season test-deps valid-spec "dev-admin")
      (is (= 1 (:ordinal @captured))))))

(deftest create-season-auto-assigns-ordinal-as-max-plus-one
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/league-by-eid          (fn [_ _] test-league)
                  data-access.contract/max-ordinal-for-league (fn [_ _] {:max-ordinal 3})
                  data-access.contract/create-season!         (fn [_ spec]
                                                                (reset! captured spec)
                                                                spec)]
      (handlers.season/create-season test-deps valid-spec "dev-admin")
      (is (= 4 (:ordinal @captured))))))

;; ─── validation errors ──────────────────────────────────────────────────────

(deftest create-season-rejects-end-before-start
  (with-redefs [data-access.contract/league-by-eid (fn [_ _] test-league)]
    (let [bad-spec (assoc valid-spec
                          :start-at (LocalDateTime/parse "2026-06-30T23:59:00")
                          :end-at   (LocalDateTime/parse "2026-04-01T00:00:00"))
          result   (handlers.season/create-season test-deps bad-spec "dev-admin")]
      (is (= :season/error (:type result))))))

(deftest create-season-rejects-non-owner
  (with-redefs [data-access.contract/league-by-eid (fn [_ _] test-league)]
    (let [result (handlers.season/create-season test-deps valid-spec "dev-someone-else")]
      (is (= :season/error (:type result))))))

(deftest create-season-rejects-missing-league
  (with-redefs [data-access.contract/league-by-eid (fn [_ _] nil)]
    (let [result (handlers.season/create-season test-deps valid-spec "dev-admin")]
      (is (= :season/error (:type result))))))

;; ─── timezone conversion ────────────────────────────────────────────────────

(deftest create-season-converts-local-datetime-to-utc-instant
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/league-by-eid          (fn [_ _] test-league)
                  data-access.contract/max-ordinal-for-league (fn [_ _] {:max-ordinal 0})
                  data-access.contract/create-season!         (fn [_ spec]
                                                                (reset! captured spec)
                                                                spec)]
      (let [spec (assoc valid-spec :timezone (ZoneId/of "America/New_York"))]
        (handlers.season/create-season test-deps spec "dev-admin"))
      (testing "instants are stored as java.time.Instant (UTC)"
        (is (instance? Instant (:start-at @captured)))
        (is (instance? Instant (:end-at @captured)))))))

;; ─── season-view-model ──────────────────────────────────────────────────────

(def ^:private test-season-eid (UUID/fromString "44444444-4444-4444-4444-444444444444"))
(def ^:private test-game-eid (UUID/fromString "22222222-2222-2222-2222-222222222222"))

(deftest season-view-model-shapes-data-league-and-tournaments
  (with-redefs [data-access.contract/season-by-eid
                (fn [_ _] {:eid      test-season-eid :ordinal 2   :name nil :league-eid test-league-eid
                           :start-at "s"             :end-at  "e"})
                data-access.contract/league-by-eid
                (fn [_ _] {:eid test-league-eid :name "Spring" :game-eid test-game-eid})
                data-access.contract/tournaments-for-game
                (fn [_ _] [{:eid (UUID/randomUUID) :season-eid test-season-eid :status "active" :name "In Season"}
                           {:eid (UUID/randomUUID) :season-eid (UUID/randomUUID) :name "Other"}])
                data-access.contract/get-faction-standings-for-season
                (fn [_ _] [])]
    (let [result (handlers.season/season-view-model test-deps test-season-eid)]
      (is (= "Season 2" (get-in result [:data :display-name])))
      (is (= "Spring" (get-in result [:league :name])))
      (is (= 1 (count (:tournaments result))) "only tournaments scoped to this season are kept")
      (is (contains? result :standings)))))

(deftest season-view-model-returns-missing-marker-when-absent
  (with-redefs [data-access.contract/season-by-eid (fn [_ _] nil)]
    (is (= :missing/resource (:type (handlers.season/season-view-model test-deps test-season-eid))))))
