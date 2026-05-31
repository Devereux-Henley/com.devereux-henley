(ns com.devereux-henley.rpfm-scraper.seed-edn-test
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [com.devereux-henley.rpfm-scraper.seed-edn :as seed-edn])
  (:import
   [java.io File]
   [java.util UUID]))

(def ^:private version "8.0")

;; --- merge-by-eid (pure) ---

(deftest merge-by-eid-attaches-generated-fields
  (let [authoring [{:unit/eid 1 :unit/name "A"} {:unit/eid 2 :unit/name "B"}]
        generated {1 {:unit/key "k1" :unit/mark :khorne}}
        merged    (seed-edn/merge-by-eid authoring generated :unit/eid)]
    (is (= {:unit/eid 1 :unit/name "A" :unit/key "k1" :unit/mark :khorne} (first merged)))
    (is (= {:unit/eid 2 :unit/name "B"} (second merged))
        "rows with no generated entry pass through unchanged")))

(deftest merge-by-eid-generated-overwrites-on-collision
  (let [merged (seed-edn/merge-by-eid [{:eid 1 :cost 0}] {1 {:cost 100}} :eid)]
    (is (= 100 (:cost (first merged))) "generated field wins over authoring placeholder")))

;; --- write-edn! ---

(deftest write-edn!-round-trips
  (let [tmp  (File/createTempFile "seed-edn-test" ".edn")
        rows [{:a 1} {:a 2}]]
    (try
      (is (= 2 (seed-edn/write-edn! tmp rows)))
      (is (= rows (edn/read-string (slurp tmp))))
      (finally (.delete tmp)))))

(deftest write-edn!-skips-empty
  (let [tmp (File/createTempFile "seed-edn-empty" ".edn")]
    (.delete tmp)
    (try
      (is (= 0 (seed-edn/write-edn! tmp [])))
      (is (not (.exists tmp)) "empty input writes no file")
      (finally (.delete tmp)))))

;; --- lookups against committed authoring/8.0 ---

(deftest faction-key->eid-maps-slugs-to-eids
  (let [k->e (seed-edn/faction-key->eid version)
        e->k (seed-edn/faction-eid->key version)]
    (is (contains? k->e "empire"))
    (is (instance? UUID (k->e "empire")))
    (is (= "empire" (e->k (k->e "empire"))) "faction-eid->key inverts faction-key->eid")))

(deftest ability+spell-key->eid-cover-full-catalogue
  (is (= 975 (count (seed-edn/ability-key->eid version))))
  (is (= 395 (count (seed-edn/spell-key->eid version))))
  (is (every? uuid? (vals (seed-edn/ability-key->eid version)))))

(deftest unit-name+faction->eid-keys-by-name-and-slug
  (let [m (seed-edn/unit-name+faction->eid version)]
    (is (= 1582 (count m)) "one entry per authoring unit row")
    (is (every? (fn [[[nm slug] eid]] (and (string? nm) (or (nil? slug) (string? slug)) (uuid? eid)))
                m))))

(deftest lore-suffix->key-canonicalises-names
  (let [m (seed-edn/lore-suffix->key version)]
    (is (seq m))
    (testing "every value is a real lore key present in authoring lores"
      (let [keys-set (into #{} (map :lore/key) (seed-edn/read-authoring version "lores.edn"))]
        (is (every? keys-set (vals m)))))))

(deftest copy-curated-list-matches-authoring-files
  (testing "every curated file exists in authoring/8.0"
    (is (every? #(.exists (io/file (seed-edn/authoring-dir version) %))
                seed-edn/curated-files))))
