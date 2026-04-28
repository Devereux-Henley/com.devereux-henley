(ns com.devereux-henley.rpfm-scraper.subfactions-seed-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rpfm-scraper.subfactions-seed :as ss]))

(def ^:private format-seed @#'ss/format-subfaction-seed)
(def ^:private slug-for    @#'ss/faction-slug-for-key)

(deftest format-subfaction-seed-empty-returns-sentinel
  (let [out (format-seed [])]
    (is (str/starts-with? out "-- no subfaction rows"))))

(deftest format-subfaction-seed-emits-insert-or-replace
  (let [rows [[1 "5f000001-0000-4000-8000-000000000000" "wh_main_emp_empire" "Empire" 1]
              [2 "5f000003-0000-4000-8000-000000000000" "wh3_dlc23_chd_legion_of_azgorh" "Legion of Azghorh" 4]]
        out  (format-seed rows)]
    (testing "uses INSERT OR REPLACE"
      (is (re-find #"INSERT OR REPLACE INTO subfaction" out)))
    (testing "emits one row per tuple"
      (is (re-find #"'wh_main_emp_empire'" out))
      (is (re-find #"'wh3_dlc23_chd_legion_of_azgorh'" out))
      (is (re-find #"'Legion of Azghorh'" out)))
    (testing "ends with a single closing semicolon"
      (is (str/ends-with? (str/trim-newline out) ";")))))

(deftest format-subfaction-seed-escapes-single-quotes
  (let [rows [[1 "00000000-0000-4000-8000-000000000001" "wh_test" "O'Driscolls" 1]]
        out  (format-seed rows)]
    (is (re-find #"'O''Driscolls'" out))))

(deftest faction-slug-for-key-matches-known-prefixes
  (testing "empire prefix"
    (is (= "empire" (slug-for "wh_main_emp_empire"))))
  (testing "chaos-dwarfs DLC prefix"
    (is (= "chaos-dwarfs" (slug-for "wh3_dlc23_chd_legion_of_azgorh"))))
  (testing "warriors-of-chaos picks chs alias"
    (is (= "warriors-of-chaos" (slug-for "wh_main_chs_chaos")))))

(deftest faction-slug-for-key-returns-nil-on-no-match
  (is (nil? (slug-for "wh_main_rebels")))
  (is (nil? (slug-for ""))))

(deftest build-faction-display-name-map-prefers-loc-over-screen-name
  (let [rows [{"key" "wh_main_emp_empire" "screen_name" "fallback_label"}
              {"key" "wh3_dlc23_chd_legion_of_azgorh" "screen_name" ""}]
        loc  {"factions_screen_name_wh_main_emp_empire"            "Empire"
              "factions_screen_name_wh3_dlc23_chd_legion_of_azgorh" "Legion of Azghorh"}
        out  (ss/build-faction-display-name-map rows loc)]
    (is (= "Empire" (get out "wh_main_emp_empire")))
    (is (= "Legion of Azghorh" (get out "wh3_dlc23_chd_legion_of_azgorh")))))

(deftest build-faction-display-name-map-falls-back-to-screen-name
  (let [rows [{"key" "wh_test" "screen_name" "Inline Name"}]
        out  (ss/build-faction-display-name-map rows {})]
    (is (= "Inline Name" (get out "wh_test")))))

(deftest build-subfaction-rows-skips-unmappable-keys
  (let [faction-rows [{"key" "wh_main_emp_empire" "screen_name" "Empire"}
                      {"key" "wh_unknown_xyz_rebels" "screen_name" "Rebels"}]
        slug->id     {"empire" 1}
        out          (ss/build-subfaction-rows faction-rows {} slug->id)]
    (is (= 1 (count out)))
    (is (= "wh_main_emp_empire" (nth (first out) 2)))))

(deftest build-subfaction-rows-produces-stable-uuids
  (let [rows [{"key" "wh_main_emp_empire" "screen_name" "Empire"}]
        slug {"empire" 1}
        a    (ss/build-subfaction-rows rows {} slug)
        b    (ss/build-subfaction-rows rows {} slug)]
    (is (= (nth (first a) 1) (nth (first b) 1)))
    (testing "valid UUID format"
      (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                      (nth (first a) 1))))))
