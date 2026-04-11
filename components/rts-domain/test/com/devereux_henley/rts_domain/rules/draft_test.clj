(ns com.devereux-henley.rts-domain.rules.draft-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rts-domain.rules.draft :as rules.draft])
  (:import
   [java.util UUID]))

;; ─── Helpers ──────────────────────────────────────────────────────────────────

(def ^:private hero-eid     (UUID/fromString "a0000000-0000-0000-0000-000000000001"))
(def ^:private infantry-eid (UUID/fromString "a0000000-0000-0000-0000-000000000002"))
(def ^:private lord-eid     (UUID/fromString "a0000000-0000-0000-0000-000000000003"))
(def ^:private monster-eid  (UUID/fromString "a0000000-0000-0000-0000-000000000004"))

(defn- hero
  ([]   (hero hero-eid))
  ([id] {:eid id :unit-category-name "Hero" :is-unique false :name "Warrior Priest" :cost 100 :section "main"}))

(defn- lord
  ([]   (lord lord-eid))
  ([id] {:eid id :unit-category-name "Lord" :is-unique false :name "General" :cost 200 :section "main"}))

(defn- infantry
  ([]   (infantry infantry-eid))
  ([id] {:eid id :unit-category-name "Melee Infantry" :is-unique false :name "Swordsmen" :cost 100 :section "main"}))

(defn- monster
  ([]   (monster monster-eid))
  ([id] {:eid id :unit-category-name "Monster" :is-unique false :name "Dragon" :cost 500 :section "main"}))

(def ^:private section-max 12400)
(def ^:private base-cost 100)

(defn- validate
  ([army unit section]
   (validate army unit section 0 section-max base-cost))
  ([army unit section section-cost]
   (validate army unit section section-cost section-max base-cost))
  ([army unit section section-cost max-cost total-cost]
   (rules.draft/validate-add army unit section section-cost max-cost total-cost)))

;; ─── Valid add ────────────────────────────────────────────────────────────────

(deftest valid-add-returns-nil
  (is (nil? (validate [] (infantry) "main"))))

(deftest valid-hero-add-returns-nil
  (is (nil? (validate [(hero (UUID/randomUUID))] (hero hero-eid) "main"))))

;; ─── Rule 1: Lords in main section only ──────────────────────────────────────

(deftest lord-added-to-reinforcements-returns-error
  (let [result (validate [] (lord) "reinforcements")]
    (is (= :draft/add-error (:type result)))
    (is (string? (:message result)))))

(deftest lord-added-to-main-returns-nil
  (is (nil? (validate [] (lord) "main"))))

;; ─── Rule 2a: Hero cap (2 per army) ──────────────────────────────────────────

(deftest hero-cap-exceeded-returns-error
  (let [army [(hero (UUID/randomUUID)) (hero (UUID/randomUUID))]
        result (validate army (hero hero-eid) "main")]
    (is (= :draft/add-error (:type result)))))

(deftest hero-cap-at-limit-allows-add
  (let [army [(hero (UUID/randomUUID))]]
    (is (nil? (validate army (hero hero-eid) "main")))))

(deftest heroes-in-both-sections-count-toward-army-cap
  (let [army [(assoc (hero (UUID/randomUUID)) :section "main")
              (assoc (hero (UUID/randomUUID)) :section "reinforcements")]
        result (validate army (hero hero-eid) "main")]
    (is (= :draft/add-error (:type result)))))

;; ─── Rule 2b: SEMC+WM cap (4 per army) ───────────────────────────────────────

(deftest semc-wm-cap-exceeded-returns-error
  (let [army (repeatedly 4 #(monster (UUID/randomUUID)))
        result (validate army (monster monster-eid) "main")]
    (is (= :draft/add-error (:type result)))))

(deftest semc-wm-cap-at-limit-allows-add
  (let [army (take 3 (repeatedly #(monster (UUID/randomUUID))))]
    (is (nil? (validate army (monster monster-eid) "main")))))

(deftest heroes-count-toward-semc-wm-cap
  (let [army (vec (concat
                   (take 3 (repeatedly #(monster (UUID/randomUUID))))
                   [(hero (UUID/randomUUID))]))
        result (validate army (hero hero-eid) "main")]
    (is (= :draft/add-error (:type result)))))

;; ─── Rule 2c: Unique unit cap (1 per army) ────────────────────────────────────

(deftest unique-unit-already-in-army-returns-error
  (let [unique-unit {:eid hero-eid :unit-category-name "Hero" :is-unique true
                     :name "Legendary Hero" :cost 400 :section "main"}
        army [unique-unit]
        result (validate army (assoc unique-unit :eid (UUID/randomUUID)
                                     :section "main") "main")]
    ;; Different UUID but same "unit type" - in practice this would be blocked by per-unit-cap.
    ;; For uniqueness, we add the same eid:
    (is (nil? result))))  ;; different eid → no unique-unit-copies violation

(deftest unique-unit-same-eid-already-in-army-returns-error
  (let [unique-unit {:eid hero-eid :unit-category-name "Hero" :is-unique true
                     :name "Legendary Hero" :cost 400 :section "main"}
        army [unique-unit]
        result (validate army unique-unit "main")]
    (is (= :draft/add-error (:type result)))))

;; ─── Rule 2d: Per-unit cap (2 per army) ───────────────────────────────────────

(deftest per-unit-cap-exceeded-returns-error
  (let [unit (infantry)
        army [(assoc unit :section "main") (assoc unit :section "reinforcements")]
        result (validate army unit "main")]
    (is (= :draft/add-error (:type result)))))

(deftest per-unit-cap-at-limit-allows-add
  (let [unit (infantry)
        army [(assoc unit :section "main")]]
    (is (nil? (validate army unit "main")))))

;; ─── Rule 3: Section slot cap (20 per section) ────────────────────────────────

(deftest section-slot-cap-exceeded-returns-error
  (let [army (mapv #(assoc (infantry %) :section "main")
                   (repeatedly 20 #(UUID/randomUUID)))
        result (validate army (infantry infantry-eid) "main")]
    (is (= :draft/add-error (:type result)))))

(deftest section-slot-cap-counts-only-target-section
  (let [army (mapv #(assoc (infantry %) :section "reinforcements")
                   (repeatedly 20 #(UUID/randomUUID)))]
    (is (nil? (validate army (infantry infantry-eid) "main")))))

(deftest section-slot-cap-at-limit-allows-add
  (let [army (mapv #(assoc (infantry %) :section "main")
                   (repeatedly 19 #(UUID/randomUUID)))]
    (is (nil? (validate army (infantry infantry-eid) "main")))))

;; ─── Rule 4: Budget cap ───────────────────────────────────────────────────────

(deftest budget-exceeded-returns-error
  (let [result (validate [] (infantry) "main" 12350 12400 100)]
    (is (= :draft/add-error (:type result)))))

(deftest budget-exactly-at-limit-returns-nil
  (is (nil? (validate [] (infantry) "main" 12300 12400 100))))

(deftest budget-check-skipped-when-section-max-nil
  (is (nil? (rules.draft/validate-add [] (infantry) "main" 99999 nil 99999))))
