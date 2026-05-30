(ns com.devereux-henley.rpfm-scraper.units-edn-test
  ;; `stat-fields` replicates rts-data-access `schema.us/fields`; the scraper
  ;; project can't see that component, so drift is caught by the verify phase's
  ;; full-seed diff (a mismatched spec changes the regenerated unit-statistics).
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rpfm-scraper.units-edn :as ue])
  (:import
   [java.util UUID]))

(def ^:private derive-mark-lore-family @#'ue/derive-mark-lore-family)

(def ^:private an-eid (UUID/randomUUID))

;; --- decode->stat-attrs ---

(deftest decode->stat-attrs-types-and-omits-empties
  (let [doc {"cost"             350                          "is_large" false "armor" 20
             "attributes"       ["encourages" "hide_forest"]
             "draftable-spells" [{"key" "s1"} {"key" "s2"}]
             "melee_modifiers"  []}             ; empty list omitted
        out (ue/decode->stat-attrs doc)]
    (is (= 350 (:unit-statistics/cost out)))
    (is (= false (:unit-statistics/is-large out)))
    (is (= ["encourages" "hide_forest"] (:unit-statistics/attributes out)))
    (is (= ["s1" "s2"] (:unit-statistics/draftable-spells out)) "spell-keys flattened from {\"key\" k}")
    (is (not (contains? out :unit-statistics/melee-modifiers)) "empty list omitted")
    (is (not (contains? out :unit-statistics/health)) "absent key omitted")))

;; --- derive-mark-lore-family (mark/lore/family composition) ---

(defn- unit [nm] {:unit/eid an-eid :unit/name nm})

(deftest derive-plain-unit
  (let [r (derive-mark-lore-family (unit "Spearmen") "k" {} {} {})]
    (is (= "Spearmen" (:family-name r)))
    (is (nil? (:mark r)))
    (is (not (contains? r :lore)))))

(deftest derive-mark-from-junction
  (let [r (derive-mark-lore-family (unit "Bloodletters") "k" {"k" "khorne"} {} {})]
    (is (= "khorne" (:mark r)))
    (is (= "Bloodletters" (:family-name r)))))

(deftest derive-strips-mark-suffix-into-family
  (let [r (derive-mark-lore-family (unit "Chaos Knights of Khorne") "k" {} {} {})]
    (is (= "Chaos Knights" (:family-name r)) "mark suffix stripped from family-name")
    (is (nil? (:mark r)) "no junction + not a lore variant ⇒ no mark")))

(deftest derive-lore-variant
  (let [r (derive-mark-lore-family (unit "Archmage (High)") "k" {} {"High" "lore-high"} {})]
    (is (= "lore-high" (:lore r)))
    (is (= "Archmage" (:family-name r)))
    (is (nil? (:mark r)))))

(deftest derive-marked-lore-variant
  (let [r (derive-mark-lore-family (unit "Chaos Sorcerer of Khorne (Death)") "k" {} {"Death" "lore-death"} {})]
    (is (= "lore-death" (:lore r)))
    (is (= "khorne" (:mark r)) "mark inferred from the name base")
    (is (= "Chaos Sorcerer" (:family-name r)) "both mark and lore suffixes stripped")))

(deftest derive-extra-lore-pin
  (let [pin {:eid (str an-eid) :lore-key "wh_main_lore_fire" :family-name "Daemon Prince"}
        r   (derive-mark-lore-family (unit "Daemon Prince") "k" {} {} {(str an-eid) pin})]
    (is (= "wh_main_lore_fire" (:lore r)))
    (is (= "Daemon Prince" (:family-name r)))))
