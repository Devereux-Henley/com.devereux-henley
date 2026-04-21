(ns com.devereux-henley.rpfm-scraper.core
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [com.devereux-henley.rpfm-scraper.abilities-seed :as abilities-seed]
   [com.devereux-henley.rpfm-scraper.assets :as assets]
   [com.devereux-henley.rpfm-scraper.items-seed :as items-seed]
   [com.devereux-henley.rpfm-scraper.mounts-seed :as mounts-seed]
   [com.devereux-henley.rpfm-scraper.name-match :as nm]
   [com.devereux-henley.rpfm-scraper.overrides :as overrides]
   [com.devereux-henley.rpfm-scraper.rpfm :as rpfm]
   [com.devereux-henley.rpfm-scraper.spells-seed :as spells-seed]
   [com.devereux-henley.rpfm-scraper.stats :as stats]
   [com.devereux-henley.rpfm-scraper.tables :as tables]
   [com.devereux-henley.rpfm-scraper.unit-items-seed :as unit-items-seed]
   [com.devereux-henley.rpfm-scraper.unit-mounts-seed :as unit-mounts-seed])
  (:gen-class))

(def ^:private seed-dir
  "components/rts-data/resources/rts-data/sql/seed")

(def ^:private unit-card-asset-dir
  "components/rts-web/resources/rts-web/asset/card/unit")

(def ^:private ability-icon-asset-dir
  "components/rts-web/resources/rts-web/asset/icon/ability")

(def ^:private item-icon-asset-dir
  "components/rts-web/resources/rts-web/asset/icon/item")

(def ^:private mount-icon-asset-dir
  "components/rts-web/resources/rts-web/asset/icon/mount")

(def ^:private stat-icon-asset-dir
  "components/rts-web/resources/rts-web/asset/icon/stat")

(def ^:private cli-options
  [["-d" "--data-dir DIR" "Directory containing RPFM-decoded JSON table files."
    :id :data-dir]
   [nil "--icons-dir DIR" "Directory containing extracted ability/spell icon PNGs."
    :id :icons-dir]
   [nil "--item-icons-dir DIR" "Extraction root containing ui/ for item icons."
    :id :item-icons-dir]
   [nil "--mount-icons-dir DIR" "Extraction root containing ui/ for mount icons."
    :id :mount-icons-dir]
   [nil "--stat-icons-dir DIR" "Extraction root containing ui/skins/default/ for stat icons."
    :id :stat-icons-dir]
   [nil "--unit-cards-dir DIR" "Directory containing extracted unit card PNGs."
    :id :unit-cards-dir]
   [nil "--portraits-dir DIR" "Directory containing extracted portrait PNGs."
    :id :portraits-dir]
   [nil "--dry-run" "Print what would change without writing files."
    :id :dry-run :default false]
   ["-h" "--help"]])

(defn- log [& args]
  (binding [*out* *err*] (apply println args)))

(defn- logf [fmt & args]
  (log (apply format fmt args)))

(defn- path-in [dir name]
  (str (io/file dir name)))

(defn- normalize-unicode-dashes [v]
  (if (string? v)
    (-> v (str/replace "\u2014" "-") (str/replace "\u2013" "-"))
    v))

(defn- load-tables [data-dir]
  (let [p #(path-in data-dir %)]
    (log "Loading game tables...")
    (let [armour-map                             (tables/build-armour-map (:rows (rpfm/parse-rpfm-table (p "unit_armour_types_tables.json"))))
          _                                      (logf "  armour types: %d" (count armour-map))

          entity-map                             (tables/build-entity-map (:rows (rpfm/parse-rpfm-table (p "battle_entities_tables.json"))))
          _                                      (logf "  battle entities: %d" (count entity-map))

          melee-map                              (tables/build-melee-weapon-map (:rows (rpfm/parse-rpfm-table (p "melee_weapons_tables.json"))))
          _                                      (logf "  melee weapons: %d" (count melee-map))

          missile-wep-map                        (tables/build-missile-weapon-map (:rows (rpfm/parse-rpfm-table (p "missile_weapons_tables.json"))))
          _                                      (logf "  missile weapons: %d" (count missile-wep-map))

          projectile-map                         (tables/build-projectile-map (:rows (rpfm/parse-rpfm-table (p "projectiles_tables.json"))))
          _                                      (logf "  projectiles: %d" (count projectile-map))

          mount-entity-map                       (tables/build-mount-entity-map
                                                  (:rows (rpfm/parse-rpfm-table (p "mounts_tables.json"))))
          _                                      (logf "  mounts: %d" (count mount-entity-map))

          engine-entity-map                      (tables/build-engine-entity-map
                                                  (:rows (rpfm/parse-rpfm-table (p "battlefield_engines_tables.json"))))
          _                                      (logf "  battlefield engines: %d" (count engine-entity-map))

          attribute-group-map                    (tables/build-attribute-group-map
                                                  (:rows (rpfm/parse-rpfm-table (p "unit_attributes_to_groups_junctions_tables.json"))))
          _                                      (logf "  unit attribute groups: %d" (count attribute-group-map))

          land-unit-stats                        (tables/build-land-unit-map
                                                  (:rows (rpfm/parse-rpfm-table (p "land_units_tables.json")))
                                                  armour-map entity-map melee-map missile-wep-map projectile-map
                                                  mount-entity-map engine-entity-map attribute-group-map)
          _                                      (logf "  land units: %d" (count land-unit-stats))

          main-unit-rows                         (:rows (rpfm/parse-rpfm-table (p "main_units_tables.json")))
          main-unit-map                          (tables/build-main-unit-map main-unit-rows)
          _                                      (logf "  main units: %d" (count main-unit-map))

          special-ability-map                    (tables/build-special-ability-map (:rows (rpfm/parse-rpfm-table (p "unit_special_abilities_tables.json"))))
          _                                      (logf "  special abilities: %d" (count special-ability-map))

          land-units-loc                         (into {}
                                                       (map (fn [[k v]] [k (normalize-unicode-dashes v)]))
                                                       (rpfm/parse-loc-file (p "land_units_loc.json")))
          _                                      (logf "  land units loc: %d entries" (count land-units-loc))

          agent-subtype-map                      (tables/build-agent-subtype-map (:rows (rpfm/parse-rpfm-table (p "agent_subtypes_tables.json"))))
          _                                      (logf "  agent subtypes: %d" (count agent-subtype-map))

          equipment-map                          (tables/build-equipment-map (:rows (rpfm/parse-rpfm-table (p "ancillaries_included_agent_subtypes_tables.json"))))
          _                                      (logf "  equipment (agent subtypes with items): %d" (count equipment-map))

          ancillaries-rows                       (:rows (rpfm/parse-rpfm-table (p "ancillaries_tables.json")))
          ancillary-cost-map                     (tables/build-ancillary-cost-map ancillaries-rows)
          _                                      (logf "  ancillary gold costs: %d" (count ancillary-cost-map))

          ancillaries-loc                        (rpfm/parse-loc-file (p "ancillaries_loc.json"))
          ancillary-name-map                     (items-seed/build-ancillary-name-map ancillaries-loc)
          _                                      (logf "  ancillary names: %d" (count ancillary-name-map))

          unit-ability-map                       (tables/build-unit-ability-map (:rows (rpfm/parse-rpfm-table (p "unit_abilities_tables.json"))))
          _                                      (logf "  unit abilities (icons): %d" (count unit-ability-map))

          anc-type-rows                          (:rows (rpfm/parse-rpfm-table (p "ancillary_types_tables.json")))
          ancillary-type-icon-map                (items-seed/build-ancillary-type-icon-map anc-type-rows)
          _                                      (logf "  ancillary type icons: %d" (count ancillary-type-icon-map))

          custom-battle-mount-rows               (:rows (rpfm/parse-rpfm-table (p "units_custom_battle_mounts_tables.json")))
          _                                      (logf "  custom battle mounts (MP): %d" (count custom-battle-mount-rows))

          ua-loc                                 (rpfm/parse-loc-file (p "unit_abilities_loc.json"))
          [ability-name-map ability-tooltip-map] (tables/build-ability-loc-maps ua-loc)
          _                                      (logf "  ability loc: %d names, %d tooltips"
                                                       (count ability-name-map) (count ability-tooltip-map))

          ability-name->key                      (tables/build-ability-name-key-map ability-name-map)
          _                                      (logf "  ability name->key map: %d entries" (count ability-name->key))

          _                                      (log "Building name index...")
          name-index                             (nm/build-name-index land-units-loc main-unit-rows)
          _                                      (logf "  %d unique unit names indexed" (count name-index))]
      {:main-unit-rows           main-unit-rows
       :main-unit-map            main-unit-map
       :land-unit-stats          land-unit-stats
       :special-ability-map      special-ability-map
       :land-units-loc           land-units-loc
       :agent-subtype-map        agent-subtype-map
       :equipment-map            equipment-map
       :ancillaries-rows         ancillaries-rows
       :ancillary-cost-map       ancillary-cost-map
       :ancillary-name-map       ancillary-name-map
       :ancillary-type-icon-map  ancillary-type-icon-map
       :custom-battle-mount-rows custom-battle-mount-rows
       :unit-ability-map         unit-ability-map
       :ability-name-map         ability-name-map
       :ability-tooltip-map      ability-tooltip-map
       :ability-name->key        ability-name->key
       :name-index               name-index})))

(defn- update-unit-seeds! [data dry-run?]
  (log "Updating unit seed files...")
  (doseq [[faction-name faction-prefixes] overrides/faction-key-map]
    (let [filename (str "seed-" faction-name "-units.sql")
          filepath (io/file seed-dir filename)]
      (if-not (.exists filepath)
        (logf "  SKIP (not found): %s" filename)
        (let [new-content (stats/update-unit-seed-file
                           (.getPath filepath)
                           faction-name faction-prefixes
                           (:name-index data)
                           (:main-unit-map data)
                           (:land-unit-stats data)
                           (:agent-subtype-map data)
                           (:equipment-map data)
                           (:ancillary-cost-map data)
                           (:ability-name->key data))]
          (when-not dry-run?
            (spit filepath new-content)))))))

(defn- update-abilities-seed! [data dry-run?]
  (log "Updating ability descriptions and costs...")
  (let [file        (io/file seed-dir "seed-abilities.sql")
        new-content (abilities-seed/update-ability-seed-file
                     (.getPath file)
                     (:ability-name-map data)
                     (:ability-tooltip-map data)
                     (:special-ability-map data))]
    (when-not dry-run?
      (spit file new-content))))

(defn- update-spell-seed! [data dry-run?]
  (log "Updating spell gold costs...")
  (let [file        (io/file seed-dir "seed-spells.sql")
        new-content (spells-seed/update-spell-seed-file
                     (.getPath file)
                     (:special-ability-map data))]
    (when-not dry-run?
      (spit file new-content))))

(defn- generate-item-seed! [data dry-run?]
  (log "Generating item seed...")
  (let [[content key->id] (items-seed/generate-item-seed
                           (:ancillaries-rows data)
                           (:ancillary-name-map data)
                           (:ancillary-type-icon-map data))]
    (logf "  %d items" (count key->id))
    (when-not dry-run?
      (spit (io/file seed-dir "seed-items.sql") content))
    key->id))

(defn- generate-unit-item-seed! [data item-key->id dry-run?]
  (log "Generating unit-item seed...")
  (let [unit-id-map (unit-items-seed/build-unit-seed-id-map seed-dir)
        _           (logf "  %d units parsed from seed files" (count unit-id-map))
        content     (unit-items-seed/generate-unit-item-seed
                     unit-id-map
                     (:name-index data)
                     (:main-unit-map data)
                     (:agent-subtype-map data)
                     (:equipment-map data)
                     item-key->id)
        row-count   (count (re-seq #"\n  \(" content))]
    (when-not dry-run?
      (spit (io/file seed-dir "seed-unit-items.sql") content))
    (logf "  %d unit-item links" row-count)
    unit-id-map))

(defn- generate-mount-seed! [data dry-run?]
  (log "Generating mount seed...")
  (let [[content stem->id] (mounts-seed/generate-mount-seed
                            (:custom-battle-mount-rows data)
                            (:ancillaries-rows data)
                            (:ancillary-name-map data)
                            (:ancillary-type-icon-map data))]
    (logf "  %d MP mounts" (count stem->id))
    (when-not dry-run?
      (spit (io/file seed-dir "seed-mounts.sql") content))
    stem->id))

(defn- generate-unit-mount-seed! [data stem->mount-id unit-id-map dry-run?]
  (log "Generating unit-mount seed...")
  (let [content (unit-mounts-seed/generate-unit-mount-seed
                 (:custom-battle-mount-rows data)
                 (:main-unit-rows data)
                 (:land-units-loc data)
                 stem->mount-id
                 unit-id-map
                 (:main-unit-map data)
                 (:land-unit-stats data)
                 (:agent-subtype-map data)
                 (:equipment-map data)
                 (:ancillary-cost-map data))]
    (when-not dry-run?
      (spit (io/file seed-dir "seed-unit-mounts.sql") content))))

(defn- copy-assets! [data opts]
  (let [{:keys [unit-cards-dir portraits-dir icons-dir item-icons-dir mount-icons-dir stat-icons-dir dry-run]} opts]
    (when (or unit-cards-dir portraits-dir)
      (log "Copying and trimming unit cards...")
      (let [pairs (assets/build-unit-name-eid-map seed-dir)]
        (logf "  %d units in seed" (count pairs))
        (if unit-cards-dir
          (assets/copy-unit-cards unit-cards-dir unit-card-asset-dir
                                  (:name-index data) pairs portraits-dir dry-run)
          (log "  [unit cards] --unit-cards-dir not provided, skipping"))
        (if portraits-dir
          (do (log "Copying and trimming lord/hero portraits...")
              (assets/copy-unit-portraits portraits-dir unit-card-asset-dir
                                          (:name-index data) pairs unit-cards-dir dry-run))
          (log "  [portraits] --portraits-dir not provided, skipping"))))
    (if icons-dir
      (do (log "Copying and trimming ability icons...")
          (let [key-eid-map (abilities-seed/build-ability-key-eid-map
                             (str (io/file seed-dir "seed-abilities.sql")))]
            (assets/copy-ability-icons icons-dir ability-icon-asset-dir
                                       (:unit-ability-map data) key-eid-map dry-run))
          (log "Copying and trimming spell icons...")
          (let [spell-eid-map (abilities-seed/build-spell-key-eid-map
                               (str (io/file seed-dir "seed-spells.sql")))]
            (assets/copy-spell-icons icons-dir ability-icon-asset-dir
                                     (:unit-ability-map data) spell-eid-map dry-run)))
      (log "  [icons] --icons-dir not provided, skipping ability/spell icon copy"))
    (if item-icons-dir
      (do (log "Copying and trimming item icons...")
          (let [item-key-type-map (items-seed/build-item-key-type-map (:ancillaries-rows data))]
            (assets/copy-item-icons item-icons-dir item-icon-asset-dir
                                    item-key-type-map (:ancillary-type-icon-map data) dry-run)))
      (log "  [item icons] --item-icons-dir not provided, skipping item icon copy"))
    (if mount-icons-dir
      (do (log "Copying and trimming mount icons...")
          (assets/copy-mount-icons mount-icons-dir mount-icon-asset-dir
                                   (:ancillaries-rows data)
                                   (:ancillary-type-icon-map data) dry-run))
      (log "  [mount icons] --mount-icons-dir not provided, skipping mount icon copy"))
    (if stat-icons-dir
      (do (log "Copying and trimming stat icons...")
          (assets/copy-stat-icons stat-icons-dir stat-icon-asset-dir dry-run))
      (log "  [stat icons] --stat-icons-dir not provided, skipping stat icon copy"))))

(defn- run [opts]
  (let [data (load-tables (:data-dir opts))
        dry? (:dry-run opts)]
    (update-unit-seeds! data dry?)
    (update-abilities-seed! data dry?)
    (copy-assets! data opts)
    (update-spell-seed! data dry?)
    (let [item-key->id (generate-item-seed! data dry?)
          unit-id-map  (generate-unit-item-seed! data item-key->id dry?)
          stem->id     (generate-mount-seed! data dry?)]
      (generate-unit-mount-seed! data stem->id unit-id-map dry?))
    (log "Done.")))

(defn -main [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (seq errors)
      (do (doseq [e errors] (log e))
          (System/exit 1))

      (:help options)
      (do (log "Update seed data from RPFM-decoded game tables.")
          (log summary))

      (str/blank? (:data-dir options))
      (do (log "--data-dir is required")
          (log summary)
          (System/exit 1))

      :else
      (run options))))
