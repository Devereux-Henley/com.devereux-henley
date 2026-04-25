(ns com.devereux-henley.rpfm-scraper.stats-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rpfm-scraper.stats :as stats]))

;; Synthetic seed-empire-units.sql fragment matching the real shape
;; (id, eid, name, description, four FK columns, then the JSON stats blob
;; on its own line — see seed-empire-units.sql for the canonical layout).
(def ^:private synthetic-seed-fragment
  (str
   "INSERT OR REPLACE INTO unit(id, eid, name, description, ...)\n"
   "VALUES\n"
   "  (36,\n"
   "   '00010024-0000-4000-8000-000000000000',\n"
   "   'Halberdiers',\n"
   "   'Melee Infantry of The Empire trained for close combat.',\n"
   "   1, 1, 1, 1, '{\"cost\":550,\"unit_size\":120}',\n"
   "   0, 1, 'sub', 'now', 'now', null),\n"
   "  (39,\n"
   "   '00010027-0000-4000-8000-000000000000',\n"
   "   'Spearmen',\n"
   "   'Melee Infantry of The Empire trained for close combat.',\n"
   "   1, 1, 1, 1, '{\"cost\":350,\"unit_size\":120}',\n"
   "   0, 1, 'sub', 'now', 'now', null);\n"))

(defn- write-tmp-seed!
  "Writes the synthetic seed fragment to a temp file and returns the path."
  []
  (let [tmp (java.io.File/createTempFile "stats-test" ".sql")]
    (.deleteOnExit tmp)
    (spit tmp synthetic-seed-fragment)
    (.getAbsolutePath tmp)))

;; Stub the lookup so we don't need real RPFM data for this test.  The
;; stub returns the engine key matching each display name so the replacer
;; treats every row as resolved.
(def ^:private stub-name-index
  {"Halberdiers" [["wh_main_emp_inf_halberdiers" "land_unit_emp_halberdiers"]]
   "Spearmen"    [["wh_main_emp_inf_spearmen_0"  "land_unit_emp_spearmen"]]})

;; We can't easily stub `extract-stats` from the outside, so feed empty
;; main-unit-map / land-unit-stats — the function will produce nil stats,
;; which means `:found` stays at zero but pairs are still recorded
;; (recording happens before the stats lookup in the new implementation).

(deftest update-unit-seed-file-returns-content-and-pairs
  (let [path   (write-tmp-seed!)
        result (stats/update-unit-seed-file
                path "empire" ["emp"]
                stub-name-index
                {} {} {} {} {} nil)]
    (testing "returns a map with :content and :pairs"
      (is (map? result))
      (is (contains? result :content))
      (is (contains? result :pairs)))
    (testing "pairs include every resolved row"
      (is (= #{["00010024-0000-4000-8000-000000000000" "wh_main_emp_inf_halberdiers"]
               ["00010027-0000-4000-8000-000000000000" "wh_main_emp_inf_spearmen_0"]}
             (set (:pairs result)))))
    (testing "content is still a string of file contents"
      (is (string? (:content result))))))

(deftest update-unit-seed-file-skips-pairs-for-unmatched-names
  (let [path   (write-tmp-seed!)
        result (stats/update-unit-seed-file
                path "empire" ["emp"]
                {} ;; empty index — no name will resolve
                {} {} {} {} {} nil)]
    (testing "no pairs collected when the name index can't resolve any name"
      (is (empty? (:pairs result))))))
