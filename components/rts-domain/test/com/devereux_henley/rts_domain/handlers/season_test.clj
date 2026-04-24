(ns com.devereux-henley.rts-domain.handlers.season-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.season :as handlers.season])
  (:import
   [java.time Instant LocalDateTime ZoneId]
   [java.util UUID]))

(def ^:private test-league-eid (UUID/fromString "11111111-1111-1111-1111-111111111111"))
(def ^:private test-deps {:connection nil})

(def ^:private test-league
  {:id      1   :eid         test-league-eid :game-eid       (UUID/randomUUID)
   :name    "L" :description "d"             :created-by-sub "dev-admin"
   :version 1   :created-at  (Instant/now)   :updated-at     (Instant/now)})

(def ^:private valid-spec
  {:eid        (UUID/randomUUID)
   :league-eid test-league-eid
   :timezone   (ZoneId/of "UTC")
   :start-at   (LocalDateTime/parse "2026-04-01T00:00:00")
   :end-at     (LocalDateTime/parse "2026-06-30T23:59:00")})

;; ─── tagging / display-name ─────────────────────────────────────────────────

(deftest get-season-by-eid-tags-and-derives-display-name
  (with-redefs [data-access.contract/get-season-by-eid
                (fn [_ _] {:id         1             :eid        (UUID/randomUUID) :league-eid test-league-eid
                           :ordinal    3             :name       nil
                           :start-at   (Instant/now) :end-at     (Instant/now)
                           :version    1             :created-at (Instant/now)     :updated-at (Instant/now)
                           :deleted-at nil})]
    (let [s (handlers.season/get-season-by-eid test-deps (UUID/randomUUID))]
      (is (= :season/season (:type s)))
      (is (= "Season 3" (:display-name s))))))

(deftest get-season-display-name-uses-override
  (with-redefs [data-access.contract/get-season-by-eid
                (fn [_ _] {:id         1             :eid        (UUID/randomUUID) :league-eid test-league-eid
                           :ordinal    1             :name       "Spring 2026"
                           :start-at   (Instant/now) :end-at     (Instant/now)
                           :version    1             :created-at (Instant/now)     :updated-at (Instant/now)
                           :deleted-at nil})]
    (is (= "Spring 2026" (:display-name (handlers.season/get-season-by-eid test-deps (UUID/randomUUID)))))))

;; ─── ordinal auto-assignment ────────────────────────────────────────────────

(deftest create-season-uses-ordinal-1-when-no-prior-seasons
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/get-league-by-eid          (fn [_ _] test-league)
                  data-access.contract/get-max-ordinal-for-league (fn [_ _] {:max-ordinal 0})
                  data-access.contract/create-season              (fn [_ spec]
                                                                    (reset! captured spec)
                                                                    (assoc spec :id 1 :deleted-at nil))]
      (handlers.season/create-season test-deps valid-spec "dev-admin")
      (is (= 1 (:ordinal @captured))))))

(deftest create-season-auto-assigns-ordinal-as-max-plus-one
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/get-league-by-eid          (fn [_ _] test-league)
                  data-access.contract/get-max-ordinal-for-league (fn [_ _] {:max-ordinal 3})
                  data-access.contract/create-season              (fn [_ spec]
                                                                    (reset! captured spec)
                                                                    (assoc spec :id 1 :deleted-at nil))]
      (handlers.season/create-season test-deps valid-spec "dev-admin")
      (is (= 4 (:ordinal @captured))))))

;; ─── validation errors ──────────────────────────────────────────────────────

(deftest create-season-rejects-end-before-start
  (with-redefs [data-access.contract/get-league-by-eid (fn [_ _] test-league)]
    (let [bad-spec (assoc valid-spec
                          :start-at (LocalDateTime/parse "2026-06-30T23:59:00")
                          :end-at   (LocalDateTime/parse "2026-04-01T00:00:00"))
          result   (handlers.season/create-season test-deps bad-spec "dev-admin")]
      (is (= :season/error (:type result))))))

(deftest create-season-rejects-non-owner
  (with-redefs [data-access.contract/get-league-by-eid (fn [_ _] test-league)]
    (let [result (handlers.season/create-season test-deps valid-spec "dev-someone-else")]
      (is (= :season/error (:type result))))))

(deftest create-season-rejects-missing-league
  (with-redefs [data-access.contract/get-league-by-eid (fn [_ _] nil)]
    (let [result (handlers.season/create-season test-deps valid-spec "dev-admin")]
      (is (= :season/error (:type result))))))

;; ─── timezone conversion ────────────────────────────────────────────────────

(deftest create-season-converts-local-datetime-to-utc-instant
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/get-league-by-eid          (fn [_ _] test-league)
                  data-access.contract/get-max-ordinal-for-league (fn [_ _] {:max-ordinal 0})
                  data-access.contract/create-season              (fn [_ spec]
                                                                    (reset! captured spec)
                                                                    (assoc spec :id 1 :deleted-at nil))]
      (let [spec (assoc valid-spec :timezone (ZoneId/of "America/New_York"))]
        (handlers.season/create-season test-deps spec "dev-admin"))
      (testing "instants are stored as java.time.Instant (UTC)"
        (is (instance? Instant (:start-at @captured)))
        (is (instance? Instant (:end-at @captured)))))))
