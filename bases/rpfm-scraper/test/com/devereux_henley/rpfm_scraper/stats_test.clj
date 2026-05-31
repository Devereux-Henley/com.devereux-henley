(ns com.devereux-henley.rpfm-scraper.stats-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rpfm-scraper.stats :as stats]))

(def ^:private unit-key "wh_main_emp_inf_halberdiers")

(def ^:private main-unit-map
  {unit-key {:land_unit "land_unit_emp_halberdiers" :mp_cost 550 :num_men 120}})

(def ^:private land-unit-stats
  {"land_unit_emp_halberdiers" {:hit_points_per_man 65 :run_speed 4 :armour 50 :morale 36}})

(deftest extract-stats-computes-core-fields
  (let [out (stats/extract-stats unit-key main-unit-map land-unit-stats nil nil nil)]
    (testing "returns an ordered stats map"
      (is (instance? java.util.Map out)))
    (testing "core fields derive from the RPFM rows"
      (is (= 550 (get out "cost")))
      (is (= 120 (get out "unit_size")))
      (is (= (* 65 120) (get out "health")))
      (is (= 50 (get out "armor")))
      ;; speed = round(10 * run_speed)
      (is (= 40 (get out "speed"))))))

(deftest extract-stats-returns-nil-without-rpfm-data
  (testing "unknown unit key"
    (is (nil? (stats/extract-stats "missing" main-unit-map land-unit-stats nil nil nil))))
  (testing "main-unit present but land-unit stats absent"
    (is (nil? (stats/extract-stats unit-key main-unit-map {} nil nil nil)))))
