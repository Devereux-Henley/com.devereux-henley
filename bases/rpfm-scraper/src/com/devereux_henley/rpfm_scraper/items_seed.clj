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

(defn icon-stem-for-row [row type-icon-map]
  (let [t   (or (get row "type") "")
        rel (get type-icon-map t)]
    (when rel
      (let [base (last (str/split rel #"/"))
            dot  (.lastIndexOf ^String base ".")]
        (if (pos? dot) (subs base 0 dot) base)))))
