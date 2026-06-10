(ns com.devereux-henley.rpfm-scraper.items-seed-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rpfm-scraper.items-seed :as items]))

;; Synthetic ancillary→effect→unit-ability chains exercising every path
;; `build-item-replay-keys-map` must cover:
;;   - chalice : a generic effect whose junction grants an `_item_passive_`
;;               ability (the common case), plus an `_item_ability_enable_`
;;               effect that is itself an item ability;
;;   - vial    : an `_item_ability_enable_` effect whose junction grants an
;;               `_item_abilities_` (plural) ability — the replay emits the
;;               `_enable`-stripped `_item_ability_` form;
;;   - plain   : only stat effects/abilities, so it contributes no keys.

(def ^:private ancillary-to-effects
  [{"ancillary" "anc_chalice" "effect" "x_generic_winds"}
   {"ancillary" "anc_chalice" "effect" "x_item_ability_enable_chalice"}
   {"ancillary" "anc_vial" "effect" "x_item_ability_enable_vial"}
   {"ancillary" "anc_plain" "effect" "x_generic_stat_melee"}])

(def ^:private effect-ability-junctions
  [{"effect" "x_generic_winds" "unit_ability" "x_item_passive_chalice"}
   {"effect" "x_item_ability_enable_chalice" "unit_ability" "x_item_passive_chalice"}
   {"effect" "x_item_ability_enable_vial" "unit_ability" "x_item_abilities_vial"}
   {"effect" "x_generic_stat_melee" "unit_ability" "x_main_stat_melee"}])

(deftest build-item-replay-keys-map-resolves-passive-and-active-forms
  (let [m (items/build-item-replay-keys-map ancillary-to-effects effect-ability-junctions)]
    (is (= ["x_item_ability_chalice"          ; effect, `_enable`-stripped
            "x_item_ability_enable_chalice"   ; the raw item-ability effect
            "x_item_passive_chalice"]         ; passive, reached via a generic effect's junction
           (get m "anc_chalice")))
    (is (= ["x_item_abilities_vial"           ; plural junction ability
            "x_item_ability_enable_vial"      ; raw item-ability effect
            "x_item_ability_vial"]            ; `_enable`-stripped form the replay emits
           (get m "anc_vial")))
    (is (not (contains? m "anc_plain")) "stat-only ancillaries contribute no replay keys")
    (is (every? vector? (vals m)) "values are sorted vecs")))
