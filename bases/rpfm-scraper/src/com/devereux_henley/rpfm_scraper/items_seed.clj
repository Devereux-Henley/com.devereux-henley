(ns com.devereux-henley.rpfm-scraper.items-seed
  (:require
   [clojure.string :as str]))

(def mp-item-categories
  "Ancillary categories that correspond to MP-selectable items in the army
  builder — everything else (campaign followers, banners, mounts) is
  excluded from seed-items.sql."
  #{"weapon" "armour" "talisman" "enchanted_item" "arcane_item"})

(def ^:private seed-author "f0ce7395-a57f-41e9-ade0-fd13bafc058f")
(def ^:private game-id 1)

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
  step stays in lockstep with generate_item_seed."
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

(defn- sql-escape [s]
  (str/replace (or s "") "'" "''"))

(defn- icon-stem-for-row [row type-icon-map]
  (let [t   (or (get row "type") "")
        rel (get type-icon-map t)]
    (when rel
      (let [base (last (str/split rel #"/"))
            dot  (.lastIndexOf ^String base ".")]
        (if (pos? dot) (subs base 0 dot) base)))))

(defn generate-item-seed
  "Full seed-items.sql regeneration. Returns [sql-content key->id]. Rows are
  sorted by ancillary key so IDs are stable across runs."
  [ancillary-rows ancillary-name-map ancillary-type-icon-map]
  (let [mp-rows     (filter #(contains? mp-item-categories (get % "category")) ancillary-rows)
        sorted-rows (sort-by #(get % "key") mp-rows)
        n           (count sorted-rows)
        header      ["INSERT OR IGNORE INTO item(id, eid, key, name, category, cost, icon_key, game_id, version, created_by_sub, created_at, updated_at, deleted_at)"
                     "VALUES"]
        indexed     (map-indexed (fn [i r] [(inc i) r]) sorted-rows)
        key->id     (into {} (map (fn [[idx r]] [(get r "key") idx])) indexed)
        row-sql
        (fn [[idx r]]
          (let [key      (get r "key")
                name     (sql-escape (or (get ancillary-name-map key) key))
                category (sql-escape (or (get r "category") ""))
                cost     (or (get r "uniqueness_score") 0)
                eid      (format "e1000000-0000-0000-0000-%012x" idx)
                icon-key (icon-stem-for-row r ancillary-type-icon-map)
                icon-sql (if icon-key (str "'" (sql-escape icon-key) "'") "null")
                comma    (if (< idx n) "," ";")]
            (format "  (%d, '%s', '%s', '%s', '%s', %d, %s, %d, 1, '%s', STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), null)%s"
                    idx eid (sql-escape key) name category (long cost)
                    icon-sql game-id seed-author comma)))
        body    (map row-sql indexed)
        content (str (str/join "\n" (concat header body)) "\n")]
    [content key->id]))
