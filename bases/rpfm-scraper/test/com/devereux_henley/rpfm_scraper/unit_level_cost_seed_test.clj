(ns com.devereux-henley.rpfm-scraper.unit-level-cost-seed-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rpfm-scraper.unit-level-cost-seed :as seed]))

(defn- row [lvl fc mul fat melee missile]
  {"xp_level"                      lvl
   "fatigue"                       fat
   "mp_fixed_cost"                 fc
   "mp_experience_cost_multiplier" mul
   "additional_melee_cp"           melee
   "additional_missile_cp"         missile})

(def ^:private full-rows
  [(row "0" 0 1.00 0 0.0 0.0)
   (row "1" 11 1.03 0 26.0 26.0)
   (row "2" 22 1.06 0 51.0 51.0)
   (row "3" 33 1.09 -1 77.0 77.0)
   (row "4" 44 1.12 -1 102.0 102.0)
   (row "5" 55 1.15 -2 128.0 128.0)
   (row "6" 66 1.18 -2 154.0 154.0)
   (row "7" 77 1.21 -3 179.0 179.0)
   (row "8" 88 1.24 -3 205.0 205.0)
   (row "9" 99 1.27 -4 230.0 230.0)])

(deftest emits-ten-rows-when-input-is-complete
  (let [{:keys [content rows missing-levels]}
        (seed/generate-unit-level-cost-seed full-rows)]
    (is (= 10 rows))
    (is (empty? missing-levels))
    (is (str/includes? content "INSERT OR REPLACE INTO unit_level_cost"))
    (testing "exactly one terminating semicolon"
      (is (= 1 (count (re-seq #";\n?$" content)))))))

(deftest reports-missing-ranks
  (testing "when ranks 3 and 7 are absent the seed still emits 8 rows but flags them"
    (let [partial-rows (filterv #(not (contains? #{"3" "7"} (get % "xp_level"))) full-rows)
          {:keys [rows missing-levels]}
          (seed/generate-unit-level-cost-seed partial-rows)]
      (is (= 8 rows))
      (is (= #{3 7} missing-levels)))))

(deftest reports-all-ranks-when-input-empty
  (let [{:keys [rows missing-levels]} (seed/generate-unit-level-cost-seed [])]
    (is (zero? rows))
    (is (= (set (range 0 10)) missing-levels))))

(deftest accepts-numeric-level-strings-and-ints
  (testing "string xp_level keys (RPFM JSON) and numeric ones both round-trip"
    (let [{:keys [rows missing-levels]}
          (seed/generate-unit-level-cost-seed
           [{"xp_level" 0 "mp_fixed_cost"       0   "mp_experience_cost_multiplier" 1.0
             "fatigue"  0 "additional_melee_cp" 0.0 "additional_missile_cp"         0.0}])]
      (is (= 1 rows))
      (is (= (set (range 1 10)) missing-levels)))))
