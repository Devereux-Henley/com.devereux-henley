(ns com.devereux-henley.rpfm-scraper.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rpfm-scraper.core :as core]))

(def ^:private format-seed
  ;; private fn — reach into the var to test the SQL shape directly
  @#'core/format-unit-keys-seed)

(deftest format-unit-keys-seed-empty-returns-sentinel
  (let [out (format-seed [])]
    (is (str/starts-with? out "-- no unit-key pairs"))))

(deftest format-unit-keys-seed-emits-single-case-update
  (let [pairs [["aaa" "wh_main_emp_inf_halberdiers"]
               ["bbb" "wh_main_emp_inf_greatswords"]]
        out   (format-seed pairs)]
    (testing "single statement (semicolons-only-at-end)"
      ;; Everything before the closing semicolon is one statement.
      (is (= 1 (count (re-seq #";\n?$" out)))))
    (testing "uses CASE eid form"
      (is (re-find #"UPDATE\s+unit\s+SET\s+key\s*=\s*CASE\s+eid" out)))
    (testing "every pair surfaces as a WHEN line"
      (is (re-find #"WHEN 'aaa' THEN 'wh_main_emp_inf_halberdiers'" out))
      (is (re-find #"WHEN 'bbb' THEN 'wh_main_emp_inf_greatswords'" out)))
    (testing "ELSE clause preserves any pre-existing key"
      (is (re-find #"ELSE\s+key" out)))))

(deftest format-unit-keys-seed-deduplicates-and-sorts
  (let [pairs [["zz" "wh_z_unit"]
               ["aa" "wh_a_unit"]
               ["aa" "wh_a_unit"]] ;; duplicate
        out   (format-seed pairs)
        whens (vec (re-seq #"WHEN '([a-z]+)' THEN" out))]
    (testing "dedups duplicate eid pairs"
      (is (= 2 (count whens))))
    (testing "stable order: sorted by eid"
      (is (= "aa" (second (first whens))))
      (is (= "zz" (second (second whens)))))))
