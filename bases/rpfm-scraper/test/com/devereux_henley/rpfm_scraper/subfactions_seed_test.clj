(ns com.devereux-henley.rpfm-scraper.subfactions-seed-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rpfm-scraper.subfactions-seed :as ss]))

(def ^:private slug-for @#'ss/faction-slug-for-key)

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
        loc  {"factions_screen_name_wh_main_emp_empire"             "Empire"
              "factions_screen_name_wh3_dlc23_chd_legion_of_azgorh" "Legion of Azghorh"}
        out  (ss/build-faction-display-name-map rows loc)]
    (is (= "Empire" (get out "wh_main_emp_empire")))
    (is (= "Legion of Azghorh" (get out "wh3_dlc23_chd_legion_of_azgorh")))))

(deftest build-faction-display-name-map-falls-back-to-screen-name
  (let [rows [{"key" "wh_test" "screen_name" "Inline Name"}]
        out  (ss/build-faction-display-name-map rows {})]
    (is (= "Inline Name" (get out "wh_test")))))

(deftest uuid-v5-produces-stable-valid-uuids
  (let [a (ss/uuid-v5 ss/subfaction-uuid-namespace "wh_main_emp_empire")
        b (ss/uuid-v5 ss/subfaction-uuid-namespace "wh_main_emp_empire")]
    (testing "deterministic for the same key"
      (is (= a b)))
    (testing "valid RFC 4122 v5 format"
      (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                      (str a))))))
