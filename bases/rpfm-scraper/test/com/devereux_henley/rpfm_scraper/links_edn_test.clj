(ns com.devereux-henley.rpfm-scraper.links-edn-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rpfm-scraper.links-edn :as le]
   [com.devereux-henley.rpfm-scraper.seed-edn :as seed-edn])
  (:import
   [java.util UUID]))

(def ^:private game-eid (UUID/fromString "eea787d7-1065-45eb-a3f6-e26f32c294a1"))

(defn- data-with [overrides]
  (merge {:ancillaries-rows         [] :ancillary-name-map {} :ancillary-type-icon-map {}
          :custom-battle-mount-rows []}
         overrides))

;; --- build-items ---

(deftest build-items-sorts-and-assigns-stable-eids
  (let [data (data-with
              {:ancillaries-rows        [{"key" "zeta_blade" "category" "weapon" "type" "t1" "uniqueness_score" 30}
                                         {"key" "alpha_charm" "category" "talisman" "uniqueness_score" 10}
                                         {"key" "skip_me" "category" "banner"}]   ; non-MP category dropped
               :ancillary-name-map      {"alpha_charm" "Alpha Charm"}
               :ancillary-type-icon-map {"t1" "ui/icons/blade.png"}})
        rows (le/build-items data game-eid)]
    (is (= 2 (count rows)) "non-MP categories excluded")
    (is (= ["alpha_charm" "zeta_blade"] (mapv :item/key rows)) "sorted by key")
    (is (= (UUID/fromString "e1000000-0000-0000-0000-000000000001") (:item/eid (first rows)))
        "index-stable eid from sorted position")
    (is (= "Alpha Charm" (:item/name (first rows))))
    (is (= "zeta_blade" (:item/name (second rows))) "name falls back to key")
    (is (= 10 (:item/cost (first rows))))
    (is (= "blade" (:item/icon-key (second rows))) "icon-key is the type icon stem")
    (is (not (contains? (first rows) :item/icon-key)) "no icon when type has none")
    (is (= [:game/eid game-eid] (:item/game (first rows))))))

;; --- build-item-abilities ---

(deftest build-item-abilities-emits-one-row-per-distinct-key
  (let [items [{:item/key "alpha_charm"}
               {:item/key "zeta_blade"}
               {:item/key "no_abilities"}]
        data  (data-with
               {:item-replay-keys-map {"alpha_charm" ["x_item_passive_shared" "a_item_ability_charm"]
                                       "zeta_blade"  ["x_item_passive_shared"]
                                       "not_emitted" ["b_item_ability_dropped"]}
                :ability-name-map     {"a_item_ability_charm" "Charm"}
                :ability-tooltip-map  {"a_item_ability_charm" "A charming aura."}
                :unit-ability-map     {"a_item_ability_charm" {:icon_name "charm" :type "wh_type_augment"}}})
        rows  (le/build-item-abilities data game-eid items)]
    (is (= ["a_item_ability_charm" "x_item_passive_shared"]
           (mapv :ability/key rows))
        "distinct keys of emitted items only, sorted")
    (is (= (seed-edn/derived-uuid "item-ability" "a_item_ability_charm")
           (:ability/eid (first rows)))
        "eid is derived from the ability key")
    (is (= ["Charm" "A charming aura." "wh_type_augment"]
           ((juxt :ability/name :ability/description :ability/ability-type) (first rows)))
        "name, description, and type come from the unit_abilities table + loc")
    (is (= #{:ability/eid :ability/key :ability/game}
           (set (keys (second rows))))
        "keys with no table/loc entry omit the display fields")
    (is (= [:game/eid game-eid] (:ability/game (first rows))))))

(deftest build-items-refs-granted-abilities
  (let [data (data-with
              {:ancillaries-rows     [{"key" "alpha_charm" "category" "talisman"}
                                      {"key" "zeta_blade" "category" "weapon"}]
               :item-replay-keys-map {"alpha_charm" ["a_item_ability_charm" "x_item_passive_shared"]}})
        rows (le/build-items data game-eid)]
    (is (= [[:ability/eid (seed-edn/derived-uuid "item-ability" "a_item_ability_charm")]
            [:ability/eid (seed-edn/derived-uuid "item-ability" "x_item_passive_shared")]]
           (:item/abilities (first rows)))
        ":item/abilities refs the item-ability rows by derived eid")
    (is (not (contains? (second rows) :item/abilities))
        "no :item/abilities when the item grants none")))

;; --- build-mounts ---

(deftest build-mounts-dedupes-stems-and-assigns-stable-eids
  (let [data (data-with
              {:custom-battle-mount-rows [{"icon_name" "ui/mounts/mount_warhorse.png"}
                                          {"icon_name" "ui/mounts/mount_barded_horse.png"}
                                          {"icon_name" "ui/mounts/mount_warhorse.png"}]   ; dup stem
               :ancillaries-rows         [{"key" "wh" "category" "mount" "type" "tm"}]
               :ancillary-name-map       {"wh" "War Horse"}
               :ancillary-type-icon-map  {"tm" "ui/mounts/mount_warhorse.png"}})
        rows (le/build-mounts data game-eid)]
    (is (= 2 (count rows)) "distinct stems only")
    (is (= ["mount_barded_horse" "mount_warhorse"] (mapv :mount/key rows)) "sorted by stem")
    (is (= (UUID/fromString "d2000000-0000-0000-0000-000000000001") (:mount/eid (first rows))))
    (is (= "Barded Horse" (:mount/name (first rows))) "name derived from stem when no ancillary name")
    (is (= "War Horse" (:mount/name (second rows))) "name from ancillary when stem resolves")
    (is (= "mount_warhorse" (:mount/icon-key (second rows))))))
