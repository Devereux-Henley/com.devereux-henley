(ns com.devereux-henley.rts-domain.handlers.replay-test
  (:require
   [clojure.java.shell :as shell]
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.replay :as handlers.replay]
   [jsonista.core :as jsonista])
  (:import
   [java.util UUID]))

(def ^:private test-eid (UUID/fromString "00000000-0000-4000-8000-000000000042"))
(def ^:private test-deps {:connection nil :replay-parser-bin "/fake/tw-replay-parser"})

(def ^:private sample-parser-output
  "Shape matches what the Rust binary emits — snake_case keys so we exercise
  the snake→kebab conversion in the handler's JSON mapper."
  {:schema_version     1
   :format             "CBAB"
   :creation_date_unix 1776993044
   :match_id           "7801776992105"
   :played_at          {:year 2026 :month 4 :day 24 :hour 3 :minute 55 :second 5}
   :victory_condition  "BATTLE_SETUP_VICTORY_CONDITION_CAPTURE_LOCATION_SCORE"
   :alliances          [{:index       0
                         :faction_key "wh_main_emp_empire"
                         :model_count 1957
                         :armies      [{:index              0
                                        :is_reinforcement   false
                                        :commander_display  "Master Engineer"
                                        :commander_portrait "ui/portraits/portholes/no_culture/emp_master_engineer_campaign_01_0.png"
                                        :faction_flag       "ui\\flags\\wh_main_emp_empire"
                                        :force_value        917
                                        :units              [{:key "wh3_dlc25_emp_cha_master_engineer_steam_tank"}
                                                             {:key "wh_main_emp_inf_spearmen_1"}]}]}
                        {:index       1
                         :faction_key "wh3_dlc23_chd_legion_of_azgorh"
                         :model_count 1816
                         :armies      [{:index              0
                                        :is_reinforcement   false
                                        :commander_display  "Sorcerer-Prophet (Fire)"
                                        :commander_portrait ""
                                        :faction_flag       "ui\\flags\\wh3_dlc23_chd_legion_of_azgorh"
                                        :force_value        782
                                        :units              [{:key "wh3_dlc23_chd_inf_chaos_dwarf_warriors"}]}]}]})

(defn- mock-shell-success
  "with-redefs helper: clojure.java.shell/sh that returns a JSON body the
  handler will parse successfully."
  [parsed]
  (fn [& _args]
    {:exit 0 :out (jsonista/write-value-as-string parsed) :err ""}))

(defn- mock-shell-failure
  [exit-code stderr]
  (fn [& _args]
    {:exit exit-code :out "" :err stderr}))

;; --- parse-replay-file ------------------------------------------------------

(deftest parse-replay-file-deserialises-rust-binary-output
  (with-redefs [shell/sh (mock-shell-success sample-parser-output)]
    (let [result (handlers.replay/parse-replay-file test-deps "/tmp/ignored.replay")]
      (testing "top-level snake_case keys become kebab keywords"
        (is (= "7801776992105" (:match-id result)))
        (is (= "CBAB" (:format result)))
        (is (= "BATTLE_SETUP_VICTORY_CONDITION_CAPTURE_LOCATION_SCORE" (:victory-condition result))))
      (testing "nested snake_case keys are also converted"
        (is (= 2 (count (:alliances result))))
        (is (= "wh_main_emp_empire" (get-in result [:alliances 0 :faction-key])))
        (is (= 917 (get-in result [:alliances 0 :armies 0 :force-value])))
        (is (= "Master Engineer" (get-in result [:alliances 0 :armies 0 :commander-display])))
        (is (= false (get-in result [:alliances 0 :armies 0 :is-reinforcement])))
        (is (= "wh3_dlc25_emp_cha_master_engineer_steam_tank"
               (get-in result [:alliances 0 :armies 0 :units 0 :key])))))))

(deftest parse-replay-file-throws-on-non-zero-exit
  (with-redefs [shell/sh (mock-shell-failure 1 "decode failed: bad magic")]
    (let [thrown (try
                   (handlers.replay/parse-replay-file test-deps "/tmp/bad.replay")
                   nil
                   (catch Exception e e))]
      (is (some? thrown))
      (is (= :error/invalid (:error/kind (ex-data thrown))))
      (is (= 1 (:exit (ex-data thrown))))
      (is (= "decode failed: bad magic" (:stderr (ex-data thrown)))))))

(deftest parse-replay-file-honours-explicit-bin-dependency
  (let [captured (atom nil)]
    (with-redefs [shell/sh (fn [& args] (reset! captured args) {:exit 0 :out "{}" :err ""})]
      (handlers.replay/parse-replay-file {:replay-parser-bin "/opt/custom-bin"} "/tmp/x.replay")
      (is (= ["/opt/custom-bin" "/tmp/x.replay"] @captured)))))

;; --- get-replay-by-eid ------------------------------------------------------

(defn- db-row-fixture []
  {:id                   1
   :eid                  test-eid
   :match-id             "7801776992105"
   :played-at            "2026-04-24T03:55:05"
   :victory-condition    "BATTLE_SETUP_VICTORY_CONDITION_CAPTURE_LOCATION_SCORE"
   :parser-format        "CBAB"
   :parsed-json          (jsonista/write-value-as-string sample-parser-output)
   :winning-alliance-idx nil
   :uploaded-by-sub      "dev-admin"
   :version              1
   :created-at           "2026-04-24T03:00:00Z"
   :updated-at           "2026-04-24T03:00:00Z"
   :deleted-at           nil})

(deftest get-replay-by-eid-returns-nil-when-not-found
  (with-redefs [data-access.contract/get-replay-by-eid (fn [_ _] nil)]
    (is (nil? (handlers.replay/get-replay-by-eid test-deps test-eid)))))

(deftest get-replay-by-eid-assigns-type
  (with-redefs [data-access.contract/get-replay-by-eid (fn [_ _] (db-row-fixture))]
    (is (= :replay/replay (:type (handlers.replay/get-replay-by-eid test-deps test-eid))))))

(deftest get-replay-by-eid-hydrates-parsed-json-into-alliances
  (with-redefs [data-access.contract/get-replay-by-eid (fn [_ _] (db-row-fixture))]
    (let [result (handlers.replay/get-replay-by-eid test-deps test-eid)]
      (is (= 2 (count (:alliances result))))
      (is (= "wh_main_emp_empire" (get-in result [:alliances 0 :faction-key]))))))

(deftest get-replay-by-eid-preserves-top-level-fields
  (with-redefs [data-access.contract/get-replay-by-eid (fn [_ _] (db-row-fixture))]
    (let [result (handlers.replay/get-replay-by-eid test-deps test-eid)]
      (is (= "7801776992105" (:match-id result)))
      (is (= "CBAB" (:parser-format result)))
      (is (= "dev-admin" (:uploaded-by-sub result)))
      (is (nil? (:winning-alliance-idx result))))))

(deftest get-replay-by-eid-strips-internal-columns
  (with-redefs [data-access.contract/get-replay-by-eid (fn [_ _] (db-row-fixture))]
    (let [result (handlers.replay/get-replay-by-eid test-deps test-eid)]
      (testing "DB bookkeeping columns don't leak into the resource"
        (is (nil? (:id result)))
        (is (nil? (:parsed-json result)))
        (is (nil? (:deleted-at result)))))))

;; --- get-replays-for-uploader -----------------------------------------------

(deftest get-replays-for-uploader-hydrates-each-row
  (with-redefs [data-access.contract/get-replays-for-uploader (fn [_ _] [(db-row-fixture) (db-row-fixture)])]
    (let [results (handlers.replay/get-replays-for-uploader test-deps "dev-admin")]
      (is (= 2 (count results)))
      (is (every? #(= :replay/replay (:type %)) results))
      (is (every? #(seq (:alliances %)) results)))))

(deftest get-replays-for-uploader-empty-result
  (with-redefs [data-access.contract/get-replays-for-uploader (fn [_ _] [])]
    (is (= [] (handlers.replay/get-replays-for-uploader test-deps "dev-admin")))))

;; --- create-replay ----------------------------------------------------------

(deftest create-replay-shells-out-inserts-and-returns-hydrated-resource
  (let [insert-call (atom nil)]
    (with-redefs [shell/sh                               (mock-shell-success sample-parser-output)
                  data-access.contract/create-replay     (fn [_conn spec]
                                                           (reset! insert-call spec)
                                                           (merge (db-row-fixture) spec))
                  data-access.contract/get-replay-by-eid (fn [_ _] (db-row-fixture))]
      (let [result (handlers.replay/create-replay
                    test-deps
                    {:eid             test-eid
                     :file-path       "/tmp/replay.replay"
                     :uploaded-by-sub "dev-admin"})]
        (testing "persisted specification carries the extracted fields"
          (let [spec @insert-call]
            (is (= test-eid (:eid spec)))
            (is (= "7801776992105" (:match-id spec)))
            (is (= "dev-admin" (:uploaded-by-sub spec)))
            (is (= "CBAB" (:parser-format spec)))
            (is (= "2026-04-24T03:55:05" (:played-at spec)))
            (is (some? (:parsed-json spec)))))
        (testing "returned resource is the hydrated domain shape"
          (is (= :replay/replay (:type result)))
          (is (= 2 (count (:alliances result)))))))))

(deftest create-replay-propagates-parse-failures
  (with-redefs [shell/sh                           (mock-shell-failure 1 "decode failed")
                data-access.contract/create-replay (fn [& _] (throw (AssertionError. "db should not be hit on parse failure")))]
    (is (thrown? Exception
                 (handlers.replay/create-replay
                  test-deps
                  {:eid             test-eid
                   :file-path       "/tmp/bad.replay"
                   :uploaded-by-sub "dev-admin"})))))

;; --- declare-winner ---------------------------------------------------------

(deftest declare-winner-persists-index-and-returns-hydrated-resource
  (let [captured-idx (atom ::unset)]
    (with-redefs [data-access.contract/update-replay-winner (fn [_ _eid idx]
                                                              (reset! captured-idx idx)
                                                              (assoc (db-row-fixture) :winning-alliance-idx idx))]
      (let [result (handlers.replay/declare-winner test-deps test-eid 0)]
        (is (= 0 @captured-idx))
        (is (= :replay/replay (:type result)))
        (is (= 0 (:winning-alliance-idx result)))))))

(deftest declare-winner-accepts-draw-sentinel
  (let [captured-idx (atom ::unset)]
    (with-redefs [data-access.contract/update-replay-winner (fn [_ _eid idx]
                                                              (reset! captured-idx idx)
                                                              (assoc (db-row-fixture) :winning-alliance-idx idx))]
      (handlers.replay/declare-winner test-deps test-eid -1)
      (is (= -1 @captured-idx)))))

(deftest declare-winner-accepts-nil-to-clear
  (let [captured-idx (atom ::unset)]
    (with-redefs [data-access.contract/update-replay-winner (fn [_ _eid idx]
                                                              (reset! captured-idx idx)
                                                              (assoc (db-row-fixture) :winning-alliance-idx idx))]
      (handlers.replay/declare-winner test-deps test-eid nil)
      (is (nil? @captured-idx)))))
