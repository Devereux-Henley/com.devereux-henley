(ns com.devereux-henley.rpfm-scraper.unit-mounts-seed
  (:require
   [clojure.string :as str]
   [com.devereux-henley.rpfm-scraper.overrides :as overrides]))

(def ^:private seed-author "f0ce7395-a57f-41e9-ade0-fd13bafc058f")

(defn- stem-of [icon-path]
  (when (seq icon-path)
    (let [base (last (str/split icon-path #"/"))
          dot  (.lastIndexOf ^String base ".")]
      (if (pos? dot) (subs base 0 dot) base))))

(defn- log [& args]
  (binding [*out* *err*] (apply println args)))

(defn generate-unit-mount-seed
  "Full seed-unit-mounts.sql regen from units_custom_battle_mounts_tables.
  Each row resolves base/mounted unit keys against main_units_tables and
  land_units loc, infers faction from the unit key, looks up unit-id via
  seed id map, and emits cost = max(0, mounted_cost - base_cost)."
  [custom-battle-mount-rows main-unit-rows land-units-loc stem->mount-id unit-id-map]
  (let [mu-by-unit        (into {}
                                (keep (fn [r]
                                        (when-let [u (get r "unit")]
                                          [u r])))
                                main-unit-rows)
        prefix->faction   (reduce-kv
                           (fn [m faction prefixes]
                             (reduce (fn [acc p]
                                       (if (contains? acc p) acc (assoc acc p faction)))
                                     m prefixes))
                           {}
                           overrides/faction-key-map)
        faction-for       (fn [unit-key]
                            (some prefix->faction (str/split unit-key #"_")))
        resolved          (atom [])
        seen              (atom #{})
        unresolved-mounts (atom #{})
        unresolved-units  (atom #{})
        missing-main      (atom #{})]
    (doseq [entry custom-battle-mount-rows]
      (let [base-key    (or (get entry "base_unit") "")
            mounted-key (or (get entry "mounted_unit") "")
            icon-name   (or (get entry "icon_name") "")]
        (when (and (seq base-key) (seq mounted-key) (seq icon-name))
          (let [base    (get mu-by-unit base-key)
                mounted (get mu-by-unit mounted-key)]
            (if-not (and base mounted)
              (swap! missing-main conj [base-key mounted-key])
              (let [lu-key    (get base "land_unit")
                    unit-name (when lu-key
                                (get land-units-loc (str "land_units_onscreen_name_" lu-key)))]
                (when unit-name
                  (when-let [faction (faction-for base-key)]
                    (if-let [unit-id (get unit-id-map [unit-name faction])]
                      (let [stem     (stem-of icon-name)
                            mount-id (get stem->mount-id stem)]
                        (if-not mount-id
                          (swap! unresolved-mounts conj stem)
                          (let [diff-cost (max 0 (- (or (get mounted "multiplayer_cost") 0)
                                                    (or (get base "multiplayer_cost") 0)))
                                pair      [unit-id mount-id]]
                            (when-not (contains? @seen pair)
                              (swap! seen conj pair)
                              (swap! resolved conj [unit-id mount-id diff-cost])))))
                      (swap! unresolved-units conj [unit-name faction]))))))))))
    (let [rows (sort @resolved)
          n    (count rows)]
      (if (zero? n)
        (do (log "  [unit-mounts] no entries resolved; emitting empty seed")
            "-- no unit_mount rows\n")
        (let [header  ["INSERT OR IGNORE INTO unit_mount(id, unit_id, mount_id, cost, version, created_by_sub, created_at, updated_at, deleted_at)"
                       "VALUES"]
              indexed (map-indexed (fn [i r] [(inc i) r]) rows)
              row-sql (fn [[idx [unit-id mount-id cost]]]
                        (let [comma (if (< idx n) "," ";")]
                          (format "  (%d, %d, %d, %d, 1, '%s', STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), STRFTIME('%%Y-%%m-%%dT%%H:%%M:%%fZ','now'), null)%s"
                                  idx unit-id mount-id cost seed-author comma)))
              body    (map row-sql indexed)
              content (str (str/join "\n" (concat header body)) "\n")]
          (log (format "  [unit-mounts] emitted %d rows from units_custom_battle_mounts_tables" n))
          (when (seq @unresolved-mounts)
            (log (format "  [unit-mounts] %d icon stems not in mount table (e.g. %s)"
                         (count @unresolved-mounts) (pr-str (take 4 (sort @unresolved-mounts))))))
          (when (seq @unresolved-units)
            (log (format "  [unit-mounts] %d units not in seed id map (e.g. %s)"
                         (count @unresolved-units) (pr-str (take 3 (sort @unresolved-units))))))
          (when (seq @missing-main)
            (log (format "  [unit-mounts] %d base/mounted key pairs not in main_units_tables"
                         (count @missing-main))))
          content)))))
