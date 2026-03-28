(ns com.devereux-henley.rts-api.handlers.tournament-test
  (:require
   [clojure.test :refer :all]
   [com.devereux-henley.rts-api.db.tournament :as db.tournament]
   [com.devereux-henley.rts-api.handlers.tournament :as handlers.tournament])
  (:import
   [java.time Instant]
   [java.util UUID]))

(def ^:private test-deps {:connection nil})
(def ^:private test-tournament-eid (UUID/randomUUID))
(def ^:private test-game-eid (UUID/randomUUID))
(def ^:private test-since (Instant/now))

;; --- get-tournaments ---

(deftest get-tournaments-assigns-type-to-each
  (with-redefs [db.tournament/get-tournaments (fn [_ _ _ _] [{:eid test-tournament-eid :title "Tournament A"}
                                                              {:eid (UUID/randomUUID) :title "Tournament B"}])]
    (let [results (handlers.tournament/get-tournaments test-deps test-since 10 0)]
      (is (every? #(= :tournament/tournament (:type %)) results)))))

(deftest get-tournaments-returns-all-results
  (with-redefs [db.tournament/get-tournaments (fn [_ _ _ _] [{:eid test-tournament-eid :title "Tournament A"}
                                                              {:eid (UUID/randomUUID) :title "Tournament B"}])]
    (is (= 2 (count (handlers.tournament/get-tournaments test-deps test-since 10 0))))))

(deftest get-tournaments-empty-result
  (with-redefs [db.tournament/get-tournaments (fn [_ _ _ _] [])]
    (is (= [] (handlers.tournament/get-tournaments test-deps test-since 10 0)))))

;; --- get-tournaments-by-game-eid ---

(deftest get-tournaments-by-game-eid-assigns-type-to-each
  (with-redefs [db.tournament/get-tournaments-by-game-eid (fn [_ _ _ _ _] [{:eid test-tournament-eid :title "Tournament A"}])]
    (let [results (handlers.tournament/get-tournaments-by-game-eid test-deps test-game-eid test-since 10 0)]
      (is (every? #(= :tournament/tournament (:type %)) results)))))

(deftest get-tournaments-by-game-eid-empty-result
  (with-redefs [db.tournament/get-tournaments-by-game-eid (fn [_ _ _ _ _] [])]
    (is (= [] (handlers.tournament/get-tournaments-by-game-eid test-deps test-game-eid test-since 10 0)))))

;; --- get-tournament-by-eid ---

(deftest get-tournament-by-eid-assigns-type
  (with-redefs [db.tournament/get-tournament-by-eid (fn [_ _] {:eid test-tournament-eid :title "Tournament A"})]
    (let [result (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)]
      (is (= :tournament/tournament (:type result))))))

(deftest get-tournament-by-eid-preserves-fields
  (with-redefs [db.tournament/get-tournament-by-eid (fn [_ _] {:eid test-tournament-eid :title "Tournament A"})]
    (let [result (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)]
      (is (= test-tournament-eid (:eid result)))
      (is (= "Tournament A" (:title result))))))

(deftest get-tournament-by-eid-nil-when-not-found
  (with-redefs [db.tournament/get-tournament-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.tournament/get-tournament-by-eid test-deps test-tournament-eid)))))

;; --- get-tournament-snapshot-by-eid ---

(deftest get-tournament-snapshot-by-eid-assigns-type
  (let [snapshot-eid (UUID/randomUUID)]
    (with-redefs [db.tournament/get-tournament-snapshot-by-eid (fn [_ _] {:eid snapshot-eid :tournament-state "active"})]
      (let [result (handlers.tournament/get-tournament-snapshot-by-eid test-deps snapshot-eid)]
        (is (= :tournament/snapshot (:type result)))))))

(deftest get-tournament-snapshot-by-eid-preserves-fields
  (let [snapshot-eid (UUID/randomUUID)]
    (with-redefs [db.tournament/get-tournament-snapshot-by-eid (fn [_ _] {:eid snapshot-eid :tournament-state "active"})]
      (let [result (handlers.tournament/get-tournament-snapshot-by-eid test-deps snapshot-eid)]
        (is (= snapshot-eid (:eid result)))
        (is (= "active" (:tournament-state result)))))))

(deftest get-tournament-snapshot-by-eid-nil-when-not-found
  (with-redefs [db.tournament/get-tournament-snapshot-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.tournament/get-tournament-snapshot-by-eid test-deps (UUID/randomUUID))))))

;; --- get-tournament-snapshot-by-tournament-eid ---

(deftest get-tournament-snapshot-by-tournament-eid-assigns-type
  (with-redefs [db.tournament/get-tournament-snapshot-by-tournament-eid (fn [_ _] {:eid (UUID/randomUUID) :tournament-state "active"})]
    (let [result (handlers.tournament/get-tournament-snapshot-by-tournament-eid test-deps test-tournament-eid)]
      (is (= :tournament/snapshot (:type result))))))

(deftest get-tournament-snapshot-by-tournament-eid-nil-when-not-found
  (with-redefs [db.tournament/get-tournament-snapshot-by-tournament-eid (fn [_ _] nil)]
    (is (nil? (handlers.tournament/get-tournament-snapshot-by-tournament-eid test-deps test-tournament-eid)))))

;; --- create-tournament ---

(deftest create-tournament-assigns-type
  (with-redefs [db.tournament/create-tournament (fn [_ spec] spec)]
    (let [result (handlers.tournament/create-tournament test-deps {:title "New Tournament"})]
      (is (= :tournament/tournament (:type result))))))

(deftest create-tournament-injects-created-at-timestamp
  (let [captured (atom nil)]
    (with-redefs [db.tournament/create-tournament (fn [_ spec] (reset! captured spec) spec)]
      (handlers.tournament/create-tournament test-deps {:title "New Tournament"})
      (is (instance? Instant (:created-at @captured))))))

(deftest create-tournament-injects-updated-at-timestamp
  (let [captured (atom nil)]
    (with-redefs [db.tournament/create-tournament (fn [_ spec] (reset! captured spec) spec)]
      (handlers.tournament/create-tournament test-deps {:title "New Tournament"})
      (is (instance? Instant (:updated-at @captured))))))

(deftest create-tournament-created-at-equals-updated-at
  (let [captured (atom nil)]
    (with-redefs [db.tournament/create-tournament (fn [_ spec] (reset! captured spec) spec)]
      (handlers.tournament/create-tournament test-deps {:title "New Tournament"})
      (is (= (:created-at @captured) (:updated-at @captured))))))

(deftest create-tournament-preserves-specification-fields
  (let [captured (atom nil)
        spec     {:title "New Tournament" :game-eid test-game-eid}]
    (with-redefs [db.tournament/create-tournament (fn [_ s] (reset! captured s) s)]
      (handlers.tournament/create-tournament test-deps spec)
      (is (= "New Tournament" (:title @captured)))
      (is (= test-game-eid (:game-eid @captured))))))

;; --- update-tournament-snapshot-by-tournament-eid ---

(deftest update-tournament-snapshot-assigns-type
  (with-redefs [db.tournament/update-tournament-snapshot-by-tournament-eid (fn [_ _ snapshot] snapshot)]
    (let [result (handlers.tournament/update-tournament-snapshot-by-tournament-eid
                  test-deps
                  test-tournament-eid
                  {:tournament-state "complete"})]
      (is (= :tournament/snapshot (:type result))))))

(deftest update-tournament-snapshot-preserves-fields
  (with-redefs [db.tournament/update-tournament-snapshot-by-tournament-eid (fn [_ _ snapshot] snapshot)]
    (let [result (handlers.tournament/update-tournament-snapshot-by-tournament-eid
                  test-deps
                  test-tournament-eid
                  {:tournament-state "complete"})]
      (is (= "complete" (:tournament-state result))))))
