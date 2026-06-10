(ns com.devereux-henley.rpfm-scraper.items-seed
  (:require
   [clojure.string :as str]))

(def mp-item-categories
  "Ancillary categories that correspond to MP-selectable items in the army
  builder — everything else (campaign followers, banners, mounts) is
  excluded from the item seed."
  #{"weapon" "armour" "talisman" "enchanted_item" "arcane_item"})

(defn build-ancillary-type-icon-map
  "type → relative ui_icon path (lowercased, .png-suffixed). Callers resolve
  the absolute path by joining against the extraction root directory."
  [rows]
  (reduce
   (fn [m r]
     (let [t       (or (get r "type") "")
           ui-icon (or (get r "ui_icon") "")]
       (if (and (seq t) (seq ui-icon))
         (let [rel (str/lower-case ui-icon)
               rel (if (str/ends-with? rel ".png") rel (str rel ".png"))]
           (assoc m t rel))
         m)))
   {}
   rows))

(defn build-item-key-type-map
  "ancillary key → type, filtered to MP_ITEM_CATEGORIES so the icon copy
  step stays in lockstep with the item seed."
  [ancillary-rows]
  (reduce (fn [m r]
            (if (contains? mp-item-categories (get r "category"))
              (assoc m (get r "key") (get r "type"))
              m))
          {}
          ancillary-rows))

(defn build-ancillary-name-map
  "{ancillary_key → display_name} from ancillaries_loc entries."
  [loc]
  (let [prefix "ancillaries_onscreen_name_"
        pn     (count prefix)]
    (reduce-kv (fn [m k v]
                 (if (and k (str/starts-with? k prefix))
                   (assoc m (subs k pn) v)
                   m))
               {}
               loc)))

(def ^:private item-ability-re
  "Matches the engine ability keys an equipped ancillary grants — the forms
  that surface in a replay's UNIT_ABILITIES: `_item_passive_<item>`,
  `_item_ability_<item>`, `_item_abilities_<item>` (the `abilit` stem covers
  the latter two)."
  #"_item_(?:passive|abilit)")

(defn- item-ability-key? [s]
  (boolean (and s (re-find item-ability-re s))))

(defn build-item-replay-keys-map
  "`{ancillary-key → sorted vec of replay ability keys}`. An equipped ancillary
  surfaces in a parsed replay's UNIT_ABILITIES as `_item_passive_…` /
  `_item_ability_…` keys, which don't match `item.key`. For each ancillary we
  collect, walking `ancillary_to_effects` ⨯
  `effect_bonus_value_unit_ability_junctions`:

   - item-ability effect keys (`…_item_ability_enable_<item>`),
   - the `_enable`-stripped form a replay emits for active items
     (`…_item_ability_<item>`),
   - the junction unit-ability keys passives resolve to
     (`…_item_passive_<item>`, `…_item_abilities_<item>`).

  Joining a replay key back through this map recovers the equipped ancillary
  without flaky cross-DLC suffix matching."
  [ancillary-to-effects-rows effect-ability-junction-rows]
  (let [eff->abilities (reduce (fn [m r]
                                 (update m (get r "effect") (fnil conj []) (get r "unit_ability")))
                               {}
                               effect-ability-junction-rows)]
    (-> (reduce
         (fn [m r]
           (let [eff (get r "effect")
                 ;; An item-passive is usually reached via a generic effect's
                 ;; junction, so collect item-ability unit-abilities for EVERY
                 ;; effect; additionally keep the effect itself when it's an
                 ;; item-ability and its `_enable`-stripped active form.
                 ks  (cond-> (into #{} (filter item-ability-key?) (get eff->abilities eff))
                       (item-ability-key? eff)
                       (conj eff)

                       (str/includes? eff "_item_ability_enable_")
                       (conj (str/replace eff "_item_ability_enable_" "_item_ability_")))]
             (cond-> m
               (seq ks) (update (get r "ancillary") (fnil into #{}) ks))))
         {}
         ancillary-to-effects-rows)
        (update-vals (comp vec sort)))))

(defn icon-stem-for-row [row type-icon-map]
  (let [t   (or (get row "type") "")
        rel (get type-icon-map t)]
    (when rel
      (let [base (last (str/split rel #"/"))
            dot  (.lastIndexOf ^String base ".")]
        (if (pos? dot) (subs base 0 dot) base)))))
