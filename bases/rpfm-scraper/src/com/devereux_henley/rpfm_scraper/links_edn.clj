(ns com.devereux-henley.rpfm-scraper.links-edn
  "Emit items.edn, mounts.edn, unit-items.edn, and unit-mounts.edn for the
  Datalog seed from RPFM data + the curated authoring identities.

  Items and mounts are fully generated with deterministic eids
  (`e1000000-…`/`d2000000-…` by sorted index). The junctions link unit eids
  (from authoring units) to item/mount eids and carry deterministic
  `derived-uuid` eids. See docs/rpfm-scraper/edn-seed-pipeline.md."
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [com.devereux-henley.rpfm-scraper.items-seed :as items]
   [com.devereux-henley.rpfm-scraper.mounts-seed :as mounts]
   [com.devereux-henley.rpfm-scraper.name-match :as nm]
   [com.devereux-henley.rpfm-scraper.overrides :as overrides]
   [com.devereux-henley.rpfm-scraper.seed-edn :as seed-edn]
   [com.devereux-henley.rpfm-scraper.stats :as stats]
   [com.devereux-henley.rpfm-scraper.unit-mounts-seed :as unit-mounts])
  (:import
   [java.util UUID]))

(defn- item-eid [idx] (UUID/fromString (format "e1000000-0000-0000-0000-%012x" idx)))
(defn- mount-eid [idx] (UUID/fromString (format "d2000000-0000-0000-0000-%012x" idx)))

(defn build-items
  "items.edn rows — MP-category ancillaries, sorted by key, with index-stable
  eids. Cost is the ancillary `uniqueness_score`."
  [data game-eid]
  (let [sorted (->> (:ancillaries-rows data)
                    (filter #(contains? items/mp-item-categories (get % "category")))
                    (sort-by #(get % "key")))]
    (into []
          (map-indexed
           (fn [i r]
             (let [k           (get r "key")
                   icon        (items/icon-stem-for-row r (:ancillary-type-icon-map data))
                   replay-keys (get (:item-replay-keys-map data) k)]
               (cond-> {:item/eid      (item-eid (inc i))
                        :item/key      k
                        :item/name     (or (get (:ancillary-name-map data) k) k)
                        :item/category (or (get r "category") "")
                        :item/cost     (long (or (get r "uniqueness_score") 0))
                        :item/game     [:game/eid game-eid]}
                 icon               (assoc :item/icon-key icon)
                 (seq replay-keys)  (assoc :item/ability-keys replay-keys)))))
          sorted)))

(defn build-mounts
  "mounts.edn rows — one per distinct MP mount icon stem, sorted, index-stable."
  [data game-eid]
  (let [stem->name (mounts/build-icon-stem->name (:ancillaries-rows data)
                                                 (:ancillary-name-map data)
                                                 (:ancillary-type-icon-map data))
        ordered    (->> (:custom-battle-mount-rows data)
                        (keep #(mounts/stem-of (get % "icon_name")))
                        (remove str/blank?)
                        set sort)]
    (into []
          (map-indexed
           (fn [i stem]
             {:mount/eid      (mount-eid (inc i))
              :mount/key      stem
              :mount/name     (or (get stem->name stem) (mounts/mount-name-from-stem stem))
              :mount/icon-key stem
              :mount/game     [:game/eid game-eid]}))
          ordered)))

(defn build-unit-items
  "unit-items.edn rows — legendary lords/heroes linked to their pre-assigned
  items, deduped by [unit-eid item-eid]."
  [data name+faction->unit-eid item-key->eid]
  (let [seen (volatile! #{})]
    (->> name+faction->unit-eid
         (mapcat
          (fn [[[nm faction] ueid]]
            (let [prefixes     (get overrides/faction-key-map faction)
                  [unit-key _] (nm/find-unit-key nm prefixes (:name-index data))
                  mu           (some->> unit-key (get (:main-unit-map data)))
                  subtype      (some->> mu :land_unit (get (:agent-subtype-map data)))
                  items        (some->> subtype (get (:equipment-map data)))]
              (for [ik    (or items [])
                    :let  [ieid (get item-key->eid ik)]
                    :when (and ieid (not (contains? @seen [ueid ieid])))]
                (do (vswap! seen conj [ueid ieid])
                    {:unit-item/eid  (seed-edn/derived-uuid "unit-item" ueid ieid)
                     :unit-item/unit [:unit/eid ueid]
                     :unit-item/item [:item/eid ieid]})))))
         (sort-by (juxt #(str (second (:unit-item/unit %)))
                        #(str (second (:unit-item/item %)))))
         vec)))

(defn- prefix->faction
  [faction-key-map]
  (reduce-kv (fn [m faction prefixes]
               (reduce (fn [acc p] (if (contains? acc p) acc (assoc acc p faction))) m prefixes))
             {} faction-key-map))

(defn build-unit-mounts
  "unit-mounts.edn rows — base→mounted variants from custom-battle mounts, with
  cost delta, the mounted statline JSON, and the abilities the mount grants."
  [data name+faction->unit-eid stem->mount-eid]
  (let [mu-by-unit (into {} (keep (fn [r] (when-let [u (get r "unit")] [u r]))) (:main-unit-rows data))
        p->f       (prefix->faction overrides/faction-key-map)
        faction-of (fn [unit-key] (some p->f (str/split unit-key #"_")))
        seen       (volatile! #{})]
    (->> (:custom-battle-mount-rows data)
         (keep
          (fn [entry]
            (let [base-key    (or (get entry "base_unit") "")
                  mounted-key (or (get entry "mounted_unit") "")
                  icon        (or (get entry "icon_name") "")
                  base        (mu-by-unit base-key)
                  mounted     (mu-by-unit mounted-key)]
              (when (and (seq base-key) (seq mounted-key) (seq icon) base mounted)
                (let [lu        (get base "land_unit")
                      mlu       (get mounted "land_unit")
                      unit-name (some->> lu (str "land_units_onscreen_name_") (get (:land-units-loc data)))
                      faction   (faction-of base-key)
                      ueid      (when (and unit-name faction)
                                  (or (get name+faction->unit-eid [unit-name faction])
                                      (when-let [m (re-matches #"(.+?) \([A-Za-z ]+\)" unit-name)]
                                        (get name+faction->unit-eid [(nth m 1) faction]))))
                      meid      (get stem->mount-eid (mounts/stem-of icon))]
                  (when (and ueid meid (not (contains? @seen [ueid meid])))
                    (vswap! seen conj [ueid meid])
                    (let [cost    (max 0 (- (or (get mounted "multiplayer_cost") 0)
                                            (or (get base "multiplayer_cost") 0)))
                          doc     (stats/extract-stats mounted-key (:main-unit-map data) (:land-unit-stats data)
                                                       (:agent-subtype-map data) (:equipment-map data)
                                                       (:ancillary-cost-map data))
                          granted (unit-mounts/granted-keys lu mlu (:land-unit-ability-map data))]
                      (cond-> {:unit-mount/eid   (seed-edn/derived-uuid "unit-mount" ueid meid)
                               :unit-mount/unit  [:unit/eid ueid]
                               :unit-mount/mount [:mount/eid meid]
                               :unit-mount/cost  cost}
                        doc     (assoc :unit-mount/stats-override
                                       (json/write-str doc :escape-slash false :escape-js-separators false))
                        granted (assoc :unit-mount/granted-ability-keys granted)))))))))
         (sort-by (juxt #(str (second (:unit-mount/unit %)))
                        #(str (second (:unit-mount/mount %)))))
         vec)))

(defn emit!
  "Write items.edn, mounts.edn, unit-items.edn, and unit-mounts.edn to the
  Datalog seed for `version`. `data` is the loaded RPFM table map."
  [version data]
  (let [game-eid    (:game/eid (first (seed-edn/read-authoring version "games.edn")))
        n+f->ueid   (seed-edn/unit-name+faction->eid version)
        item-rows   (build-items data game-eid)
        mount-rows  (build-mounts data game-eid)
        item-key->e (into {} (map (juxt :item/key :item/eid)) item-rows)
        stem->meid  (into {} (map (juxt :mount/key :mount/eid)) mount-rows)]
    (seed-edn/write-seed-file! version "items.edn" item-rows)
    (seed-edn/write-seed-file! version "mounts.edn" mount-rows)
    (seed-edn/write-seed-file! version "unit-items.edn" (build-unit-items data n+f->ueid item-key->e))
    (seed-edn/write-seed-file! version "unit-mounts.edn" (build-unit-mounts data n+f->ueid stem->meid))))
