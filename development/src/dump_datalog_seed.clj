(ns dump-datalog-seed
  "Offline dev tool: snapshot the current SQL seed (which the rpfm-scraper
  produces) as Datalog EDN seed files under
  `components/rts-data/resources/rts-data/seed/datalog/<patch-version>/`.

  Run after each `rpfm-scraper` refresh to publish a new patch's seed:

    (dump-datalog-seed/dump! \"8.0\")

  Boots a clean in-memory-via-tempfile SQLite, runs every migration and
  every SQL seed file, then queries each entity table and writes one EDN
  file per entity type (matching the loader's `seed-files` order in
  `rts-data.datalog-seed`). Idempotent — re-running for the same patch
  overwrites the existing files. The output is meant to be committed.

  This tool is dev-only; it is **not** loaded by the API base."
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [com.devereux-henley.rts-data-access.schema.datalog.unit-statistics :as schema.us]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [jsonista.core :as jsonista]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
  (:import
   [java.nio.charset StandardCharsets]
   [java.nio.file Files]
   [java.util Date UUID]))

;;; ─── Helpers ──────────────────────────────────────────────────────────────

(def ^:private default-jdbc-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(def ^:private object-mapper
  (jsonista/object-mapper {:decode-key-fn name}))

(defn- query
  [conn sql]
  (jdbc/execute! conn [sql] default-jdbc-opts))

(defn- uuid
  [s]
  (when s (UUID/fromString s)))

(defn- derived-uuid
  [& parts]
  (UUID/nameUUIDFromBytes
   (.getBytes (str/join "/" parts) StandardCharsets/UTF_8)))

(defn- ->bool
  [n]
  (= 1 n))

(def ^:private seed-root-dir
  "components/rts-data/resources/rts-data/seed/datalog")

(defn- spit-edn!
  "Pretty-print `data` to `file`. Skips writing entirely when `data` is
  empty so the loader's `keep` filter elides absent files."
  [file data]
  (if (empty? data)
    (println (format "  %-30s SKIP (empty)" (.getName file)))
    (do
      (.mkdirs (.getParentFile file))
      (with-open [w (io/writer file)]
        (binding [*out* w]
          (pp/pprint data)))
      (println (format "  %-30s %d entities" (.getName file) (count data))))))

;;; ─── Per-entity dumpers ───────────────────────────────────────────────────

(defn- dump-games
  [conn]
  (mapv
   (fn [r]
     {:game/eid         (uuid (:eid r))
      :game/name        (:name r)
      :game/description (:description r)})
   (query conn "SELECT eid, name, description FROM game WHERE deleted_at IS NULL")))

(defn- dump-social-media-platforms
  [conn]
  (mapv
   (fn [r]
     {:social-media-platform/eid          (uuid (:eid r))
      :social-media-platform/name         (:name r)
      :social-media-platform/description  (:description r)
      :social-media-platform/platform-url (:platform-url r)})
   (query conn "SELECT eid, name, description, platform_url
                  FROM social_media_platform WHERE deleted_at IS NULL")))

(defn- dump-game-social-links
  [conn]
  (mapv
   (fn [r]
     {:game-social-link/eid      (uuid (:eid r))
      :game-social-link/url      (:url r)
      :game-social-link/game     [:game/eid (uuid (:game-eid r))]
      :game-social-link/platform [:social-media-platform/eid (uuid (:platform-eid r))]})
   (query conn
          "SELECT gsl.eid, gsl.url, g.eid AS game_eid, smp.eid AS platform_eid
             FROM game_social_link gsl
             JOIN game g ON gsl.game_id = g.id
             JOIN social_media_platform smp ON gsl.social_media_platform_id = smp.id
            WHERE gsl.deleted_at IS NULL")))

(defn- dump-unit-types
  [conn]
  (mapv
   (fn [r]
     {:unit-type/eid         (uuid (:eid r))
      :unit-type/name        (:name r)
      :unit-type/description (:description r)
      :unit-type/game        [:game/eid (uuid (:game-eid r))]})
   (query conn
          "SELECT ut.eid, ut.name, ut.description, g.eid AS game_eid
             FROM unit_type ut JOIN game g ON ut.game_id = g.id
            WHERE ut.deleted_at IS NULL")))

(defn- dump-unit-categories
  [conn]
  (mapv
   (fn [r]
     {:unit-category/eid         (uuid (:eid r))
      :unit-category/name        (:name r)
      :unit-category/description (:description r)
      :unit-category/game        [:game/eid (uuid (:game-eid r))]})
   (query conn
          "SELECT uc.eid, uc.name, uc.description, g.eid AS game_eid
             FROM unit_category uc JOIN game g ON uc.game_id = g.id
            WHERE uc.deleted_at IS NULL")))

(defn- dump-factions
  [conn]
  (mapv
   (fn [r]
     (cond-> {:faction/eid         (uuid (:eid r))
              :faction/name        (:name r)
              :faction/description (:description r)
              :faction/game        [:game/eid (uuid (:game-eid r))]}
       (:key r) (assoc :faction/key (:key r))))
   (query conn
          "SELECT f.eid, f.name, f.key, f.description, g.eid AS game_eid
             FROM faction f JOIN game g ON f.game_id = g.id
            WHERE f.deleted_at IS NULL")))

(defn- dump-subfactions
  [conn]
  (mapv
   (fn [r]
     {:subfaction/eid     (uuid (:eid r))
      :subfaction/key     (:key r)
      :subfaction/name    (:name r)
      :subfaction/faction [:faction/eid (uuid (:faction-eid r))]})
   (query conn
          "SELECT sf.eid, sf.key, sf.name, f.eid AS faction_eid
             FROM subfaction sf JOIN faction f ON sf.faction_id = f.id
            WHERE sf.deleted_at IS NULL")))

(defn- dump-game-modes
  [conn]
  (mapv
   (fn [r]
     {:game-mode/eid                    (uuid (:eid r))
      :game-mode/name                   (:name r)
      :game-mode/description            (:description r)
      :game-mode/draft-value            (:draft-value r)
      :game-mode/player-count           (:player-count r)
      :game-mode/reinforcement-value    (:reinforcement-value r)
      :game-mode/reinforcements-enabled (->bool (:reinforcements-enabled r))
      :game-mode/game                   [:game/eid (uuid (:game-eid r))]})
   (query conn
          "SELECT gm.eid, gm.name, gm.description, gm.draft_value,
                  gm.player_count, gm.reinforcement_value,
                  gm.reinforcements_enabled, g.eid AS game_eid
             FROM game_mode gm JOIN game g ON gm.game_id = g.id
            WHERE gm.deleted_at IS NULL")))

(defn- dump-lores
  [conn]
  (mapv
   (fn [r]
     {:lore/eid         (uuid (:eid r))
      :lore/key         (:key r)
      :lore/name        (:name r)
      :lore/description (:description r)
      :lore/game        [:game/eid (uuid (:game-eid r))]})
   (query conn
          "SELECT l.eid, l.key, l.name, l.description, g.eid AS game_eid
             FROM lore l JOIN game g ON l.game_id = g.id
            WHERE l.deleted_at IS NULL")))

(defn- dump-spells
  [conn]
  (mapv
   (fn [r]
     {:spell/eid         (uuid (:eid r))
      :spell/key         (:key r)
      :spell/name        (:name r)
      :spell/description (:description r)
      :spell/spell-type  (:spell-type r)
      :spell/mana-cost   (:mana-cost r)
      :spell/cost        (:cost r)
      :spell/game        [:game/eid (uuid (:game-eid r))]})
   (query conn
          "SELECT s.eid, s.key, s.name, s.description, s.spell_type,
                  s.mana_cost, s.cost, g.eid AS game_eid
             FROM spell s JOIN game g ON s.game_id = g.id
            WHERE s.deleted_at IS NULL")))

(defn- dump-spell-lores
  [conn]
  (mapv
   (fn [r]
     {:spell-lore/eid   (uuid (:eid r))
      :spell-lore/spell [:spell/eid (uuid (:spell-eid r))]
      :spell-lore/lore  [:lore/eid (uuid (:lore-eid r))]
      :spell-lore/game  [:game/eid (uuid (:game-eid r))]})
   (query conn
          "SELECT sl.eid, s.eid AS spell_eid, l.eid AS lore_eid, g.eid AS game_eid
             FROM spell_lore sl
             JOIN spell s ON sl.spell_id = s.id
             JOIN lore l ON sl.lore_id = l.id
             JOIN game g ON sl.game_id = g.id
            WHERE sl.deleted_at IS NULL")))

(defn- dump-abilities
  [conn]
  (mapv
   (fn [r]
     {:ability/eid          (uuid (:eid r))
      :ability/key          (:key r)
      :ability/name         (:name r)
      :ability/description  (:description r)
      :ability/ability-type (:ability-type r)
      :ability/cost         (:cost r)
      :ability/game         [:game/eid (uuid (:game-eid r))]})
   (query conn
          "SELECT a.eid, a.key, a.name, a.description, a.ability_type, a.cost,
                  g.eid AS game_eid
             FROM ability a JOIN game g ON a.game_id = g.id
            WHERE a.deleted_at IS NULL")))

(defn- dump-attributes
  [conn]
  (mapv
   (fn [r]
     {:attribute/eid         (uuid (:eid r))
      :attribute/key         (:key r)
      :attribute/name        (:name r)
      :attribute/description (:description r)
      :attribute/game        [:game/eid (uuid (:game-eid r))]})
   (query conn
          "SELECT a.eid, a.key, a.name, a.description, g.eid AS game_eid
             FROM attribute a JOIN game g ON a.game_id = g.id
            WHERE a.deleted_at IS NULL")))

(defn- dump-items
  [conn]
  (mapv
   (fn [r]
     (cond-> {:item/eid      (uuid (:eid r))
              :item/key      (:key r)
              :item/name     (:name r)
              :item/category (:category r)
              :item/cost     (:cost r)
              :item/game     [:game/eid (uuid (:game-eid r))]}
       (:icon-key r) (assoc :item/icon-key (:icon-key r))))
   (query conn
          "SELECT i.eid, i.key, i.name, i.category, i.cost, i.icon_key,
                  g.eid AS game_eid
             FROM item i JOIN game g ON i.game_id = g.id
            WHERE i.deleted_at IS NULL")))

(defn- dump-mounts
  [conn]
  (mapv
   (fn [r]
     (cond-> {:mount/eid  (uuid (:eid r))
              :mount/key  (:key r)
              :mount/name (:name r)
              :mount/game [:game/eid (uuid (:game-eid r))]}
       (:icon-key r) (assoc :mount/icon-key (:icon-key r))))
   (query conn
          "SELECT m.eid, m.key, m.name, m.icon_key, g.eid AS game_eid
             FROM mount m JOIN game g ON m.game_id = g.id
            WHERE m.deleted_at IS NULL")))

(defn- dump-unit-items
  [conn]
  (mapv
   (fn [r]
     (let [u (uuid (:unit-eid r))
           i (uuid (:item-eid r))]
       {:unit-item/eid  (derived-uuid "unit-item" u i)
        :unit-item/unit [:unit/eid u]
        :unit-item/item [:item/eid i]}))
   (query conn
          "SELECT u.eid AS unit_eid, i.eid AS item_eid
             FROM unit_item ui
             JOIN unit u ON ui.unit_id = u.id
             JOIN item i ON ui.item_id = i.id
            WHERE ui.deleted_at IS NULL")))

(defn- dump-unit-mounts
  [conn]
  (mapv
   (fn [r]
     (let [u (uuid (:unit-eid r))
           m (uuid (:mount-eid r))]
       (cond-> {:unit-mount/eid   (derived-uuid "unit-mount" u m)
                :unit-mount/unit  [:unit/eid u]
                :unit-mount/mount [:mount/eid m]
                :unit-mount/cost  (:cost r)}
         (:stats-override r)
         (assoc :unit-mount/stats-override (:stats-override r))
         (and (:granted-ability-keys r) (not (str/blank? (:granted-ability-keys r))))
         (assoc :unit-mount/granted-ability-keys
                (jsonista/read-value (:granted-ability-keys r) object-mapper)))))
   (query conn
          "SELECT u.eid AS unit_eid, m.eid AS mount_eid,
                  um.cost, um.stats_override, um.granted_ability_keys
             FROM unit_mount um
             JOIN unit u ON um.unit_id = u.id
             JOIN mount m ON um.mount_id = m.id
            WHERE um.deleted_at IS NULL")))

(defn- dump-unit-level-cost
  [conn]
  (mapv
   (fn [r]
     {:unit-level-cost/level           (:level r)
      :unit-level-cost/fixed-cost      (:fixed-cost r)
      :unit-level-cost/cost-multiplier (:cost-multiplier r)
      :unit-level-cost/fatigue         (:fatigue r)
      :unit-level-cost/melee-cp        (:melee-cp r)
      :unit-level-cost/missile-cp      (:missile-cp r)})
   (query conn
          "SELECT level, fixed_cost, cost_multiplier, fatigue, melee_cp, missile_cp
             FROM unit_level_cost")))

;;; ─── Units + unit-statistics ──────────────────────────────────────────────

(defn- dump-unit-rows
  [conn]
  (query conn
         "SELECT u.eid, u.key, u.name, u.family_name, u.description,
                 u.unit_statistics, u.mark, u.lore, u.is_unique,
                 g.eid AS game_eid,
                 f.eid AS faction_eid,
                 ut.eid AS unit_type_eid,
                 uc.eid AS unit_category_eid
            FROM unit u
            JOIN game g ON u.game_id = g.id
            JOIN faction f ON u.faction_id = f.id
            JOIN unit_type ut ON u.unit_type_id = ut.id
            JOIN unit_category uc ON u.unit_category_id = uc.id
           WHERE u.deleted_at IS NULL"))

(defn- ->unit-tx
  [r]
  (cond-> {:unit/eid           (uuid (:eid r))
           :unit/name          (:name r)
           :unit/description   (:description r)
           :unit/is-unique     (->bool (:is-unique r))
           :unit/game          [:game/eid (uuid (:game-eid r))]
           :unit/faction       [:faction/eid (uuid (:faction-eid r))]
           :unit/unit-type     [:unit-type/eid (uuid (:unit-type-eid r))]
           :unit/unit-category [:unit-category/eid (uuid (:unit-category-eid r))]}
    (:key r)         (assoc :unit/key (:key r))
    (:family-name r) (assoc :unit/family-name (:family-name r))
    (:mark r)        (assoc :unit/mark (keyword (:mark r)))
    (:lore r)        (assoc :unit/lore (:lore r))))

(defn decoded->stat-attrs
  "Decompose an engine-shaped, string-keyed statistics map into the
   `:unit-statistics/*` attributes defined by `schema.us/fields`. Empty
   lists are omitted (cardinality-many stores nothing); `:spell-keys`
   pull the `\"key\"` out of each `{\"key\" k}` map."
  [decoded]
  (reduce (fn [attrs [doc-key attr kind]]
            (let [v (get decoded doc-key)]
              (case kind
                :long       (if (some? v) (assoc attrs attr (long v)) attrs)
                :boolean    (if (some? v) (assoc attrs attr (boolean v)) attrs)
                :strings    (if (seq v) (assoc attrs attr (vec v)) attrs)
                :spell-keys (let [ks (mapv #(get % "key") v)]
                              (if (seq ks) (assoc attrs attr ks) attrs)))))
          {} schema.us/fields))

(defn- ->unit-statistics-tx
  "Build a `:unit-statistics` tx map with one typed attribute per stat
   field (see `schema.us/fields`)."
  [{:keys [unit-eid unit-statistics-eid patch-eid stats-json]}]
  (let [decoded (jsonista/read-value stats-json object-mapper)]
    (merge {:unit-statistics/eid   unit-statistics-eid
            :unit-statistics/unit  [:unit/eid unit-eid]
            :unit-statistics/patch [:patch/eid patch-eid]}
           (decoded->stat-attrs decoded))))

;;; ─── Orchestrator ─────────────────────────────────────────────────────────

(defn- temp-sqlite-spec!
  "Create a fresh file-backed SQLite db so all of `next.jdbc`'s connections
  (migration, seed, dump) see the same data."
  []
  (let [tmp (Files/createTempFile "rts-data-dump-" ".db" (make-array java.nio.file.attribute.FileAttribute 0))]
    (.deleteOnExit (.toFile tmp))
    {:dbtype "sqlite" :dbname (str tmp)}))

(defn dump!
  "Snapshot the current SQL seed as Datalog EDN under
  `seed/datalog/<patch-version>/`. Boots a clean SQLite, applies every
  migration + every SQL seed file, then writes one EDN file per entity.

  `opts`:
    :patch-released-at — `java.util.Date` for `:patch/released-at`. When
                         not supplied, an existing `patches.edn` is read
                         and its timestamp preserved so that re-dumps
                         don't clobber the ordering invariant; if no
                         prior dump exists, defaults to now.
    :out-dir           — override the output directory (default:
                         `components/rts-data/resources/rts-data/seed/datalog/`)."
  ([patch-version] (dump! patch-version {}))
  ([patch-version {:keys [patch-released-at out-dir]}]
   (let [db-spec      (temp-sqlite-spec!)
         out-root     (or (some-> out-dir io/file) (io/file seed-root-dir))
         dest         (io/file out-root patch-version)
         patches-file (io/file dest "patches.edn")
         existing-ts  (when (.exists patches-file)
                        (-> patches-file slurp read-string first :patch/released-at))
         effective-ts (or patch-released-at existing-ts (Date.))
         patch-eid    (derived-uuid "patch" patch-version)]
     (println (format "Dumping patch %s → %s" patch-version (.getPath dest)))
     (println "  Bootstrapping SQLite from SQL seed…")
     (migratus/migrate {:store         :database
                        :migration-dir rts-data/migration-dir
                        :db            db-spec})
     (rts-data/seed-db db-spec)
     (with-open [conn (jdbc/get-connection db-spec)]
       (println "\nWriting EDN seed files:")
       (spit-edn! patches-file
                  [{:patch/eid         patch-eid
                    :patch/version     patch-version
                    :patch/released-at effective-ts}])
       (spit-edn! (io/file dest "games.edn")                  (dump-games conn))
       (spit-edn! (io/file dest "social-media-platforms.edn") (dump-social-media-platforms conn))
       (spit-edn! (io/file dest "unit-level-cost.edn")        (dump-unit-level-cost conn))
       (spit-edn! (io/file dest "game-social-links.edn")      (dump-game-social-links conn))
       (spit-edn! (io/file dest "unit-types.edn")             (dump-unit-types conn))
       (spit-edn! (io/file dest "unit-categories.edn")        (dump-unit-categories conn))
       (spit-edn! (io/file dest "factions.edn")               (dump-factions conn))
       (spit-edn! (io/file dest "game-modes.edn")             (dump-game-modes conn))
       (spit-edn! (io/file dest "lores.edn")                  (dump-lores conn))
       (spit-edn! (io/file dest "spells.edn")                 (dump-spells conn))
       (spit-edn! (io/file dest "abilities.edn")              (dump-abilities conn))
       (spit-edn! (io/file dest "attributes.edn")             (dump-attributes conn))
       (spit-edn! (io/file dest "items.edn")                  (dump-items conn))
       (spit-edn! (io/file dest "mounts.edn")                 (dump-mounts conn))
       (spit-edn! (io/file dest "subfactions.edn")            (dump-subfactions conn))
       (spit-edn! (io/file dest "spell-lores.edn")            (dump-spell-lores conn))

       ;; Units + decomposed statistics (one :unit-statistics per unit
       ;; per patch, linked from the many side via :unit-statistics/unit).
       (let [unit-rows (dump-unit-rows conn)
             stats     (mapv (fn [r]
                               {:unit-statistics-eid
                                (derived-uuid "unit-statistics" patch-eid (uuid (:eid r)))
                                :patch-eid           patch-eid
                                :stats-json          (:unit-statistics r)
                                :unit-eid            (uuid (:eid r))})
                             unit-rows)]
         (spit-edn! (io/file dest "units.edn")
                    (mapv ->unit-tx unit-rows))
         (spit-edn! (io/file dest "unit-items.edn")
                    (dump-unit-items conn))
         (spit-edn! (io/file dest "unit-mounts.edn")
                    (dump-unit-mounts conn))
         (spit-edn! (io/file dest "unit-statistics.edn")
                    (mapv ->unit-statistics-tx stats))))
     (println (format "\nDone. %s seed published." patch-version)))))

(comment
  ;; After refreshing the SQL seeds with rpfm-scraper, dump a new patch:
  (dump! "8.0"))
