(ns com.devereux-henley.rpfm-scraper.catalog-edn-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rpfm-scraper.catalog-edn :as ce]))

(def ^:private version "8.0")

;; Empty RPFM lookups isolate the merge/order/default behaviour over the real
;; committed authoring EDN — no RPFM table load needed.

(deftest build-spells-merges-cost-and-keeps-canonical-order
  (let [rows (ce/build-spells version {:special-ability-map {}})]
    (is (= 395 (count rows)))
    (is (every? #(= 0 (:spell/cost %)) rows) "cost defaults to 0 when RPFM lacks the key")
    (is (every? :spell/name rows) "curated name preserved from authoring")
    (is (every? :spell/mana-cost rows) "curated mana-cost preserved")
    (is (= [:spell/eid :spell/key :spell/name :spell/description
            :spell/spell-type :spell/mana-cost :spell/cost :spell/game]
           (vec (keys (first rows))))
        "canonical key order for a stable seed")))

(deftest build-abilities-merges-name-cost-and-keeps-description
  (let [rows (ce/build-abilities version {:ability-name-map {} :special-ability-map {}})]
    (is (= 975 (count rows)))
    (is (every? #(nil? (:ability/name %)) rows) "name comes from RPFM (nil here)")
    (is (every? #(= 0 (:ability/cost %)) rows) "cost defaults to 0")
    (is (every? :ability/description rows) "curated description preserved from authoring")
    (is (= [:ability/eid :ability/key :ability/name :ability/description
            :ability/ability-type :ability/cost :ability/game]
           (vec (keys (first rows))))
        "canonical key order")))

(deftest build-subfactions-resolves-parent-factions
  (let [rows (ce/build-subfactions version "bases/rpfm-scraper/data")]
    (is (= 626 (count rows)))
    (is (every? #(uuid? (:subfaction/eid %)) rows) "stable UUID-v5 eids")
    (is (every? #(and (seq (:subfaction/key %)) (seq (:subfaction/name %))) rows))
    (is (every? #(= :faction/eid (first (:subfaction/faction %))) rows)
        "every subfaction resolves a parent faction eid")
    (is (apply distinct? (map :subfaction/eid rows)) "eids unique")))
