(ns com.devereux-henley.rts-data.datalog-seed-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rts-data.datalog-seed :as datalog-seed]))

;; The committed 8.0 seed is on the test classpath, so these exercise the real
;; resource set rather than a fixture.

(deftest missing-seed-files-empty-for-complete-patch
  (is (= [] (datalog-seed/missing-seed-files "8.0"))
      "every declared seed file is present for the committed patch"))

(deftest missing-seed-files-lists-all-for-unknown-patch
  (is (= datalog-seed/seed-files (datalog-seed/missing-seed-files "does-not-exist"))
      "an unknown patch has every file missing, in transact order"))

(deftest ensure-patch-version-passes-for-complete-patch
  (is (nil? (datalog-seed/ensure-patch-version "8.0"))
      "a complete seed does not throw"))

(deftest ensure-patch-version-never-parses-edn
  (testing "the guard probes resource presence only — no EDN is read"
    (with-redefs [datalog-seed/load-file-tx
                  (fn [& _] (throw (AssertionError. "ensure-patch-version parsed a seed file")))]
      (is (nil? (datalog-seed/ensure-patch-version "8.0"))))))

(deftest ensure-patch-version-rejects-unknown-patch
  (let [ex (is (thrown? clojure.lang.ExceptionInfo
                        (datalog-seed/ensure-patch-version "does-not-exist")))]
    (is (re-find #"No Datalog seed files found" (ex-message ex)))
    (is (contains? (set (:available (ex-data ex))) "8.0")
        "the error advertises the patches that do exist")))

(deftest ensure-patch-version-rejects-partial-dump
  (testing "a dump missing only a suffix of files is reported as incomplete"
    (let [dropped #{"unit-statistics.edn" "unit-mounts.edn"}
          present (remove dropped datalog-seed/seed-files)]
      (with-redefs [datalog-seed/seed-files present]
        (is (nil? (datalog-seed/ensure-patch-version "8.0"))
            "control: the trimmed set is itself complete"))
      ;; Simulate a crash that wrote everything except the dropped files.
      (with-redefs [datalog-seed/missing-seed-files (fn [_] (vec dropped))]
        (let [ex (is (thrown? clojure.lang.ExceptionInfo
                              (datalog-seed/ensure-patch-version "8.0")))]
          (is (re-find #"Incomplete Datalog seed" (ex-message ex)))
          (is (= (vec dropped) (:missing (ex-data ex)))))))))
