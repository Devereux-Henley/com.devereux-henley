(ns com.devereux-henley.rpfm-scraper.mounts-seed
  (:require
   [clojure.string :as str]))

(def ^:private seed-author "f0ce7395-a57f-41e9-ade0-fd13bafc058f")
(def ^:private game-id 1)

(defn- sql-escape [s]
  (str/replace (or s "") "'" "''"))

(defn- stem-of [icon-path]
  (when (seq icon-path)
    (let [base (last (str/split icon-path #"/"))
          dot  (.lastIndexOf ^String base ".")]
      (if (pos? dot) (subs base 0 dot) base))))

(defn- mount-name-from-stem [stem]
  (let [s (if (str/starts-with? stem "mount_") (subs stem (count "mount_")) stem)]
    (if (seq s)
      (->> (str/split s #"_")
           (map str/capitalize)
           (str/join " "))
      stem)))

(defn- build-icon-stem->name
  "{icon_stem → display_name} by resolving every mount-category ancillary
  to its type's ui_icon basename; first-seen wins for dup stems."
  [ancillary-rows ancillary-name-map type-icon-map]
  (reduce
   (fn [m a]
     (if (= "mount" (get a "category"))
       (let [rel (get type-icon-map (or (get a "type") ""))]
         (if rel
           (let [stem (stem-of rel)]
             (if (and (seq stem) (not (contains? m stem)))
               (if-let [nm (get ancillary-name-map (get a "key"))]
                 (assoc m stem nm)
                 m)
               m))
           m))
       m))
   {}
   ancillary-rows))

(defn generate-mount-seed
  "Full seed-mounts.sql regen from units_custom_battle_mounts_tables. One
  row per distinct icon stem (e.g. `mount_barded_warhorse`) referenced by
  an MP-available mount entry. Returns [sql-content stem->id]."
  [custom-battle-mount-rows ancillary-rows ancillary-name-map type-icon-map]
  (let [stem->name (build-icon-stem->name ancillary-rows ancillary-name-map type-icon-map)
        stems      (->> custom-battle-mount-rows
                        (keep #(stem-of (get % "icon_name")))
                        (remove str/blank?)
                        set)
        ordered    (sort stems)
        n          (count ordered)
        indexed    (map-indexed (fn [i s] [(inc i) s]) ordered)
        stem->id   (into {} (map (fn [[i s]] [s i])) indexed)
        header     ["INSERT OR IGNORE INTO mount(id, eid, key, name, icon_key, game_id, version, created_by_sub, created_at, updated_at, deleted_at)"
                    "VALUES"]
        row-sql
        (fn [[idx stem]]
          (let [name  (or (get stem->name stem) (mount-name-from-stem stem))
                eid   (format "d2000000-0000-0000-0000-%012x" idx)
                comma (if (< idx n) "," ";")]
            (format "  (%d, '%s', '%s', '%s', '%s', %d, 1, '%s', STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), null)%s"
                    idx eid (sql-escape stem) (sql-escape name) (sql-escape stem)
                    game-id seed-author comma)))
        body       (map row-sql indexed)
        content    (str (str/join "\n" (concat header body)) "\n")]
    [content stem->id]))
