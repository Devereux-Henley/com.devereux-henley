(ns com.devereux-henley.rpfm-scraper.core
  "Refresh the Datalog seed for a patch from RPFM-decoded game tables: read the
  curated authoring EDN + RPFM JSON and write the merged seed under
  components/rts-data/resources/rts-data/seed/datalog/<patch-version>/.
  See docs/rpfm-scraper/edn-seed-pipeline.md."
  (:require
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [com.devereux-henley.rpfm-scraper.assets :as assets]
   [com.devereux-henley.rpfm-scraper.catalog-edn :as catalog-edn]
   [com.devereux-henley.rpfm-scraper.items-seed :as items-seed]
   [com.devereux-henley.rpfm-scraper.links-edn :as links-edn]
   [com.devereux-henley.rpfm-scraper.name-match :as nm]
   [com.devereux-henley.rpfm-scraper.rpfm :as rpfm]
   [com.devereux-henley.rpfm-scraper.seed-edn :as seed-edn]
   [com.devereux-henley.rpfm-scraper.tables :as tables]
   [com.devereux-henley.rpfm-scraper.units-edn :as units-edn])
  (:gen-class))

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
   [nil "--patch-version VERSION" "Patch version whose Datalog seed to refresh."
    :id :patch-version :default "8.0"]
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
   [nil "--strict" "Exit non-zero when any unit row lacks a key or PNG, or any PNG is stale."
    :id :strict :default false]
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

          item-replay-keys-map                   (items-seed/build-item-replay-keys-map
                                                  (:rows (rpfm/parse-rpfm-table (p "ancillary_to_effects_tables.json")))
                                                  (:rows (rpfm/parse-rpfm-table (p "effect_bonus_value_unit_ability_junctions_tables.json"))))
          _                                      (logf "  item replay-ability keys: %d items" (count item-replay-keys-map))

          land-xp-bonus-file                     (io/file (p "unit_stats_land_experience_bonuses_tables.json"))
          land-xp-bonus-rows                     (when (.exists land-xp-bonus-file)
                                                   (:rows (rpfm/parse-rpfm-table (.getPath land-xp-bonus-file))))
          _                                      (logf "  land XP bonuses (per-rank cost): %d rows"
                                                       (count land-xp-bonus-rows))

          land-unit-ability-map                  (tables/build-land-unit-ability-map
                                                  (:rows (rpfm/parse-rpfm-table (p "land_units_to_unit_abilites_junctions_tables.json"))))
          _                                      (logf "  land_unit → abilities: %d entries" (count land-unit-ability-map))

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
       :land-unit-ability-map    land-unit-ability-map
       :special-ability-map      special-ability-map
       :land-units-loc           land-units-loc
       :agent-subtype-map        agent-subtype-map
       :equipment-map            equipment-map
       :ancillaries-rows         ancillaries-rows
       :ancillary-cost-map       ancillary-cost-map
       :ancillary-name-map       ancillary-name-map
       :ancillary-type-icon-map  ancillary-type-icon-map
       :custom-battle-mount-rows custom-battle-mount-rows
       :item-replay-keys-map     item-replay-keys-map
       :land-xp-bonus-rows       land-xp-bonus-rows
       :unit-ability-map         unit-ability-map
       :ability-name-map         ability-name-map
       :ability-tooltip-map      ability-tooltip-map
       :ability-name->key        ability-name->key
       :name-index               name-index})))

(defn- key->eid-strings
  "`{key → eid-string}` for asset filenames, from an authoring `key → eid` map."
  [m]
  (into {} (map (fn [[k v]] [k (str v)])) m))

(defn- copy-assets! [data version opts]
  (let [{:keys [unit-cards-dir portraits-dir icons-dir item-icons-dir mount-icons-dir stat-icons-dir dry-run]} opts]
    (when (or unit-cards-dir portraits-dir)
      (log "Copying and trimming unit cards...")
      (let [pairs               (seed-edn/unit-name-eid-faction version)
            _                   (logf "  %d units in seed" (count pairs))
            unmatched-card-eids (if unit-cards-dir
                                  (assets/copy-unit-cards unit-cards-dir unit-card-asset-dir
                                                          (:name-index data) pairs portraits-dir dry-run)
                                  (do (log "  [unit cards] --unit-cards-dir not provided, skipping")
                                      #{}))]
        (if portraits-dir
          (do (log "Copying and trimming lord/hero portraits...")
              (assets/copy-unit-portraits portraits-dir unit-card-asset-dir
                                          (:name-index data) pairs unit-cards-dir
                                          unmatched-card-eids dry-run))
          (log "  [portraits] --portraits-dir not provided, skipping"))))
    (if icons-dir
      (do (log "Copying and trimming ability icons...")
          (let [key-eid-map (key->eid-strings (seed-edn/ability-key->eid version))]
            (assets/copy-ability-icons icons-dir ability-icon-asset-dir
                                       (:unit-ability-map data) key-eid-map dry-run))
          (log "Copying and trimming spell icons...")
          (let [spell-eid-map (key->eid-strings (seed-edn/spell-key->eid version))]
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

(def ^:private placeholder-png-stem
  "Filename stem (without .png) for the optional placeholder image.  Excluded
  from the stale-PNG check so a hand-added `placeholder.png` doesn't trip
  --strict.  Mirrors the issue-45 acceptance criteria."
  "placeholder")

(defn- list-png-stems
  "Stem names (without `.png`) of every PNG file directly in `dir`, or `#{}`
  if `dir` doesn't exist."
  [dir]
  (let [d (io/file dir)]
    (if (.isDirectory d)
      (->> (.list d)
           (filter #(str/ends-with? % ".png"))
           (map #(subs % 0 (- (count %) 4)))
           set)
      #{})))

(defn compute-coverage
  "Compute the unit-coverage report from the canonical seed-file unit list,
  the unit-key pairs collected during the stats pass, and the unit-card
  asset directory state.

  - `unit-name-eid-pairs`: `[[name eid faction] ...]` covering every unit
    row in the seed (output of `seed-edn/unit-name-eid-faction`).
  - `unit-key-pairs`: `[[eid unit-key] ...]` collected by the stats pass.
  - `asset-dir`: directory whose `<eid>.png` files are the post-match
    modal's source of unit portraits.

  Returns:
    {:total                <count of unit rows>
     :missing-keys         [<sorted distinct names with no engine key>]
     :missing-icons        [<sorted distinct names with no <eid>.png>]
     :stale-pngs           [<sorted eid stems present on disk but not in seed>]
     :missing-level-ranks  [<sorted ranks 0-9 missing from unit_level_cost seed>]}"
  ([unit-name-eid-pairs unit-key-pairs asset-dir]
   (compute-coverage unit-name-eid-pairs unit-key-pairs asset-dir #{}))
  ([unit-name-eid-pairs unit-key-pairs asset-dir missing-level-ranks]
   (let [all-eids     (set (map second unit-name-eid-pairs))
         keyed-eids   (set (map first unit-key-pairs))
         png-stems    (list-png-stems asset-dir)
         missing-key  (->> unit-name-eid-pairs
                           (remove (fn [[_ eid _]] (contains? keyed-eids eid)))
                           (map first)
                           distinct
                           sort
                           vec)
         missing-icon (->> unit-name-eid-pairs
                           (remove (fn [[_ eid _]] (contains? png-stems eid)))
                           (map first)
                           distinct
                           sort
                           vec)
         stale-pngs   (->> png-stems
                           (remove #(or (= % placeholder-png-stem)
                                        (contains? all-eids %)))
                           sort
                           vec)]
     {:total               (count unit-name-eid-pairs)
      :missing-keys        missing-key
      :missing-icons       missing-icon
      :stale-pngs          stale-pngs
      :missing-level-ranks (vec (sort missing-level-ranks))})))

(defn coverage-clean?
  "True when every unit row has a key, every unit row has a PNG, no PNG is
  stale, and the unit-level-cost seed covers ranks 0-9.  Drives the --strict
  exit code."
  [coverage]
  (and (empty? (:missing-keys coverage))
       (empty? (:missing-icons coverage))
       (empty? (:stale-pngs coverage))
       (empty? (:missing-level-ranks coverage))))

(defn- write-coverage-manifest!
  "Spit `target/scraper-coverage.json` with the coverage report so a CI run
  has a single artefact to diff between scrapes.  Always written (even in
  non-strict mode) per the issue-45 spec; skipped under --dry-run."
  [coverage dry-run?]
  (log "Writing coverage manifest...")
  (let [target-dir (io/file "target")
        path       (io/file target-dir "scraper-coverage.json")]
    (cond
      dry-run?
      (logf "  [coverage] DRY: would write %s" (.getPath path))

      :else
      (do
        (.mkdirs target-dir)
        (spit path (str (json/write-str coverage :escape-slash false) "\n"))
        (logf "  [coverage] %s — total: %d, missing-keys: %d, missing-icons: %d, stale-pngs: %d, missing-level-ranks: %d"
              (.getPath path)
              (:total coverage)
              (count (:missing-keys coverage))
              (count (:missing-icons coverage))
              (count (:stale-pngs coverage))
              (count (:missing-level-ranks coverage)))))))

(defn- log-coverage-examples!
  "Print up to 5 example names per missing-keys / missing-icons bucket, plus
  every stale PNG.  Helps a developer triaging a --strict failure see what
  to fix without opening the manifest."
  [coverage]
  (when (seq (:missing-keys coverage))
    (logf "  [coverage] missing-keys (%d), e.g. %s"
          (count (:missing-keys coverage))
          (pr-str (take 5 (:missing-keys coverage)))))
  (when (seq (:missing-icons coverage))
    (logf "  [coverage] missing-icons (%d), e.g. %s"
          (count (:missing-icons coverage))
          (pr-str (take 5 (:missing-icons coverage)))))
  (when (seq (:stale-pngs coverage))
    (logf "  [coverage] stale-pngs (%d), e.g. %s"
          (count (:stale-pngs coverage))
          (pr-str (take 5 (:stale-pngs coverage)))))
  (when (seq (:missing-level-ranks coverage))
    (logf "  [coverage] missing-level-ranks (%d): %s"
          (count (:missing-level-ranks coverage))
          (pr-str (:missing-level-ranks coverage)))))

(defn- unit-key-pairs
  "`[[eid-string unit-key] …]` for every emitted unit that resolved an engine
  key — drives the coverage report's missing-key check."
  [version]
  (->> (seed-edn/read-edn-file (io/file (seed-edn/datalog-dir version) "units.edn"))
       (keep (fn [u] (when (:unit/key u) [(str (:unit/eid u)) (:unit/key u)])))
       vec))

(defn- emit-seed!
  "Write the merged Datalog seed for `version`: curated authoring copied
  through, then the generated entities."
  [version data data-dir]
  (log (format "Writing Datalog seed for patch %s…" version))
  (seed-edn/copy-curated! version)
  (units-edn/emit! version data data-dir)
  (links-edn/emit! version data)
  (catalog-edn/emit! version data data-dir)
  (log "  seed written."))

(defn- run [opts]
  (let [version (:patch-version opts)
        data    (load-tables (:data-dir opts))
        dry?    (:dry-run opts)
        strict? (:strict opts)]
    (if dry?
      (log "[dry-run] skipping seed writes")
      (emit-seed! version data (:data-dir opts)))
    (copy-assets! data version opts)
    (let [unit-name-eid-pairs (seed-edn/unit-name-eid-faction version)
          coverage            (compute-coverage unit-name-eid-pairs
                                                (unit-key-pairs version)
                                                unit-card-asset-dir)]
      (write-coverage-manifest! coverage dry?)
      (log-coverage-examples! coverage)
      (cond
        (coverage-clean? coverage)
        (log "Done.")

        strict?
        (do (logf "STRICT: %d missing keys, %d missing icons, %d stale PNGs, %d missing level ranks — failing run"
                  (count (:missing-keys coverage))
                  (count (:missing-icons coverage))
                  (count (:stale-pngs coverage))
                  (count (:missing-level-ranks coverage)))
            (System/exit 1))

        :else
        (log "Done (with coverage gaps; pass --strict to fail loud).")))))

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
