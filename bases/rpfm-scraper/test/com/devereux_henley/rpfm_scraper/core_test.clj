(ns com.devereux-henley.rpfm-scraper.core-test
  (:require
   [clojure.java.io :as io]
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

;; ---------------------------------------------------------------------------
;; Coverage-manifest helpers (see com.devereux-henley.rpfm-scraper.core).
;; ---------------------------------------------------------------------------

(defn- mk-asset-dir!
  "Make a temp asset-dir whose `<stem>.png` files are the given stems."
  [stems]
  (let [d (java.io.File/createTempFile "scraper-cov" "")]
    (.delete d) (.mkdirs d) (.deleteOnExit d)
    (doseq [s stems]
      (let [f (io/file d (str s ".png"))]
        (spit f "")
        (.deleteOnExit f)))
    (.getAbsolutePath d)))

(deftest compute-coverage-clean-when-everything-resolves
  (let [unit-name-eid-pairs [["Halberdiers" "eid-1" "empire"]
                             ["Spearmen"    "eid-2" "empire"]]
        unit-key-pairs      [["eid-1" "wh_main_emp_inf_halberdiers"]
                             ["eid-2" "wh_main_emp_inf_spearmen_0"]]
        asset-dir           (mk-asset-dir! ["eid-1" "eid-2"])
        cov                 (core/compute-coverage unit-name-eid-pairs
                                                   unit-key-pairs
                                                   asset-dir)]
    (is (= 2 (:total cov)))
    (is (empty? (:missing-keys cov)))
    (is (empty? (:missing-icons cov)))
    (is (empty? (:stale-pngs cov)))
    (is (true? (core/coverage-clean? cov)))))

(deftest compute-coverage-flags-missing-key
  (let [unit-name-eid-pairs [["Halberdiers" "eid-1" "empire"]
                             ["Spearmen"    "eid-2" "empire"]]
        unit-key-pairs      [["eid-1" "wh_main_emp_inf_halberdiers"]] ;; eid-2 unresolved
        asset-dir           (mk-asset-dir! ["eid-1" "eid-2"])
        cov                 (core/compute-coverage unit-name-eid-pairs
                                                   unit-key-pairs
                                                   asset-dir)]
    (is (= ["Spearmen"] (:missing-keys cov)))
    (is (empty? (:missing-icons cov)))
    (is (false? (core/coverage-clean? cov)))))

(deftest compute-coverage-flags-missing-icon
  (let [unit-name-eid-pairs [["Halberdiers" "eid-1" "empire"]
                             ["Spearmen"    "eid-2" "empire"]]
        unit-key-pairs      [["eid-1" "wh_main_emp_inf_halberdiers"]
                             ["eid-2" "wh_main_emp_inf_spearmen_0"]]
        asset-dir           (mk-asset-dir! ["eid-1"]) ;; eid-2.png missing
        cov                 (core/compute-coverage unit-name-eid-pairs
                                                   unit-key-pairs
                                                   asset-dir)]
    (is (empty? (:missing-keys cov)))
    (is (= ["Spearmen"] (:missing-icons cov)))
    (is (false? (core/coverage-clean? cov)))))

(deftest compute-coverage-flags-stale-png
  (let [unit-name-eid-pairs [["Halberdiers" "eid-1" "empire"]]
        unit-key-pairs      [["eid-1" "wh_main_emp_inf_halberdiers"]]
        asset-dir           (mk-asset-dir! ["eid-1" "eid-stale"])
        cov                 (core/compute-coverage unit-name-eid-pairs
                                                   unit-key-pairs
                                                   asset-dir)]
    (is (empty? (:missing-keys cov)))
    (is (empty? (:missing-icons cov)))
    (is (= ["eid-stale"] (:stale-pngs cov)))
    (is (false? (core/coverage-clean? cov)))))

(deftest compute-coverage-allows-placeholder-png
  (testing "placeholder.png is exempt from the stale-PNG check"
    (let [unit-name-eid-pairs [["Halberdiers" "eid-1" "empire"]]
          unit-key-pairs      [["eid-1" "wh_main_emp_inf_halberdiers"]]
          asset-dir           (mk-asset-dir! ["eid-1" "placeholder"])
          cov                 (core/compute-coverage unit-name-eid-pairs
                                                     unit-key-pairs
                                                     asset-dir)]
      (is (empty? (:stale-pngs cov)))
      (is (true? (core/coverage-clean? cov))))))

(deftest compute-coverage-deduplicates-names-across-factions
  (testing "the same display name in two factions only appears once in missing-keys"
    (let [unit-name-eid-pairs [["Mortars" "eid-a" "empire"]
                               ["Mortars" "eid-b" "vampire-coast"]]
          unit-key-pairs      [] ;; nothing resolved
          asset-dir           (mk-asset-dir! [])
          cov                 (core/compute-coverage unit-name-eid-pairs
                                                     unit-key-pairs
                                                     asset-dir)]
      (is (= ["Mortars"] (:missing-keys cov)))
      (is (= ["Mortars"] (:missing-icons cov))))))

(deftest compute-coverage-handles-missing-asset-dir
  (testing "a missing asset-dir behaves like an empty one"
    (let [unit-name-eid-pairs [["Halberdiers" "eid-1" "empire"]]
          unit-key-pairs      [["eid-1" "wh_main_emp_inf_halberdiers"]]
          cov                 (core/compute-coverage unit-name-eid-pairs
                                                     unit-key-pairs
                                                     "/nonexistent/path/scraper-cov")]
      (is (= ["Halberdiers"] (:missing-icons cov)))
      (is (empty? (:stale-pngs cov))))))

(deftest compute-coverage-flags-missing-level-ranks
  (testing "missing veteran ranks make coverage dirty"
    (let [unit-name-eid-pairs [["Halberdiers" "eid-1" "empire"]]
          unit-key-pairs      [["eid-1" "wh_main_emp_inf_halberdiers"]]
          asset-dir           (mk-asset-dir! ["eid-1"])
          cov                 (core/compute-coverage unit-name-eid-pairs
                                                     unit-key-pairs
                                                     asset-dir
                                                     #{3 7})]
      (is (= [3 7] (:missing-level-ranks cov)))
      (is (false? (core/coverage-clean? cov))))))
