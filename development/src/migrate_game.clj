(ns migrate-game
  "One-shot migration: read the game-domain seed from SQLite and transact
  it into Datalevin.

  Idempotent. Every entity carries a `:db.unique/identity` attribute
  (`:<entity>/eid`), so re-running upserts in place instead of inserting
  duplicates. Junction-table entities that lack an eid in SQLite
  (`unit_item`, `unit_mount`) get a deterministic UUIDv3 derived from
  the parent eids. `:unit-statistics` and `:unit-stat` use the same
  trick (`patch-eid + unit-eid` / `unit-statistics-eid + stat-key`).

  Will be deleted once the rts-data SQLite seed pipeline is decommissioned."
  (:require
   [clojure.string :as str]
   [com.devereux-henley.datalog.contract :as datalog]
   [com.devereux-henley.rts-api.datalog :as rts-datalog]
   [com.devereux-henley.rts-api.db :as rts-db]
   [com.devereux-henley.rts-data-access.contract :as rda]
   [jsonista.core :as jsonista]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
  (:import
   [java.nio.charset StandardCharsets]
   [java.time Instant]
   [java.util Date UUID]))

;;; ─── Helpers ──────────────────────────────────────────────────────────────

(def ^:private default-jdbc-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(def ^:private object-mapper
  (jsonista/object-mapper {:decode-key-fn name}))

(defn- query
  [sqlite-conn sql]
  (jdbc/execute! sqlite-conn [sql] default-jdbc-opts))

(defn- uuid
  [s]
  (when s (UUID/fromString s)))

(defn- derived-uuid
  "Deterministic UUIDv3 from a / -joined seed. Stable across runs so the
   seed loader can upsert entities that lack a natural eid in SQLite."
  [& parts]
  (UUID/nameUUIDFromBytes
   (.getBytes (str/join "/" parts) StandardCharsets/UTF_8)))

(defn- ->bool
  "SQLite stores booleans as integers."
  [n]
  (= 1 n))

;;; ─── Per-entity loaders ───────────────────────────────────────────────────

(defn- load-games
  [sqlite-conn]
  (mapv
   (fn [r]
     {:game/eid         (uuid (:eid r))
      :game/name        (:name r)
      :game/description (:description r)})
   (query sqlite-conn
          "SELECT eid, name, description
             FROM game
            WHERE deleted_at IS NULL")))

(defn- load-social-media-platforms
  [sqlite-conn]
  (mapv
   (fn [r]
     {:social-media-platform/eid          (uuid (:eid r))
      :social-media-platform/name         (:name r)
      :social-media-platform/description  (:description r)
      :social-media-platform/platform-url (:platform-url r)})
   (query sqlite-conn
          "SELECT eid, name, description, platform_url
             FROM social_media_platform
            WHERE deleted_at IS NULL")))

(defn- load-game-social-links
  [sqlite-conn]
  (mapv
   (fn [r]
     {:game-social-link/eid      (uuid (:eid r))
      :game-social-link/url      (:url r)
      :game-social-link/game     [:game/eid (uuid (:game-eid r))]
      :game-social-link/platform [:social-media-platform/eid (uuid (:platform-eid r))]})
   (query sqlite-conn
          "SELECT gsl.eid, gsl.url, g.eid AS game_eid, smp.eid AS platform_eid
             FROM game_social_link gsl
             JOIN game g ON gsl.game_id = g.id
             JOIN social_media_platform smp ON gsl.social_media_platform_id = smp.id
            WHERE gsl.deleted_at IS NULL")))

(defn- load-unit-types
  [sqlite-conn]
  (mapv
   (fn [r]
     {:unit-type/eid         (uuid (:eid r))
      :unit-type/name        (:name r)
      :unit-type/description (:description r)
      :unit-type/game        [:game/eid (uuid (:game-eid r))]})
   (query sqlite-conn
          "SELECT ut.eid, ut.name, ut.description, g.eid AS game_eid
             FROM unit_type ut
             JOIN game g ON ut.game_id = g.id
            WHERE ut.deleted_at IS NULL")))

(defn- load-unit-categories
  [sqlite-conn]
  (mapv
   (fn [r]
     {:unit-category/eid         (uuid (:eid r))
      :unit-category/name        (:name r)
      :unit-category/description (:description r)
      :unit-category/game        [:game/eid (uuid (:game-eid r))]})
   (query sqlite-conn
          "SELECT uc.eid, uc.name, uc.description, g.eid AS game_eid
             FROM unit_category uc
             JOIN game g ON uc.game_id = g.id
            WHERE uc.deleted_at IS NULL")))

(defn- load-factions
  [sqlite-conn]
  (mapv
   (fn [r]
     (cond-> {:faction/eid         (uuid (:eid r))
              :faction/name        (:name r)
              :faction/description (:description r)
              :faction/game        [:game/eid (uuid (:game-eid r))]}
       (:key r) (assoc :faction/key (:key r))))
   (query sqlite-conn
          "SELECT f.eid, f.name, f.key, f.description, g.eid AS game_eid
             FROM faction f
             JOIN game g ON f.game_id = g.id
            WHERE f.deleted_at IS NULL")))

(defn- load-subfactions
  [sqlite-conn]
  (mapv
   (fn [r]
     {:subfaction/eid     (uuid (:eid r))
      :subfaction/key     (:key r)
      :subfaction/name    (:name r)
      :subfaction/faction [:faction/eid (uuid (:faction-eid r))]})
   (query sqlite-conn
          "SELECT sf.eid, sf.key, sf.name, f.eid AS faction_eid
             FROM subfaction sf
             JOIN faction f ON sf.faction_id = f.id
            WHERE sf.deleted_at IS NULL")))

(defn- load-game-modes
  [sqlite-conn]
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
   (query sqlite-conn
          "SELECT gm.eid, gm.name, gm.description, gm.draft_value,
                  gm.player_count, gm.reinforcement_value,
                  gm.reinforcements_enabled, g.eid AS game_eid
             FROM game_mode gm
             JOIN game g ON gm.game_id = g.id
            WHERE gm.deleted_at IS NULL")))

(defn- load-lores
  [sqlite-conn]
  (mapv
   (fn [r]
     {:lore/eid         (uuid (:eid r))
      :lore/key         (:key r)
      :lore/name        (:name r)
      :lore/description (:description r)
      :lore/game        [:game/eid (uuid (:game-eid r))]})
   (query sqlite-conn
          "SELECT l.eid, l.key, l.name, l.description, g.eid AS game_eid
             FROM lore l
             JOIN game g ON l.game_id = g.id
            WHERE l.deleted_at IS NULL")))

(defn- load-spells
  [sqlite-conn]
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
   (query sqlite-conn
          "SELECT s.eid, s.key, s.name, s.description, s.spell_type,
                  s.mana_cost, s.cost, g.eid AS game_eid
             FROM spell s
             JOIN game g ON s.game_id = g.id
            WHERE s.deleted_at IS NULL")))

(defn- load-spell-lores
  [sqlite-conn]
  (mapv
   (fn [r]
     {:spell-lore/eid   (uuid (:eid r))
      :spell-lore/spell [:spell/eid (uuid (:spell-eid r))]
      :spell-lore/lore  [:lore/eid (uuid (:lore-eid r))]
      :spell-lore/game  [:game/eid (uuid (:game-eid r))]})
   (query sqlite-conn
          "SELECT sl.eid, s.eid AS spell_eid, l.eid AS lore_eid, g.eid AS game_eid
             FROM spell_lore sl
             JOIN spell s ON sl.spell_id = s.id
             JOIN lore l ON sl.lore_id = l.id
             JOIN game g ON sl.game_id = g.id
            WHERE sl.deleted_at IS NULL")))

(defn- load-abilities
  [sqlite-conn]
  (mapv
   (fn [r]
     {:ability/eid          (uuid (:eid r))
      :ability/key          (:key r)
      :ability/name         (:name r)
      :ability/description  (:description r)
      :ability/ability-type (:ability-type r)
      :ability/cost         (:cost r)
      :ability/game         [:game/eid (uuid (:game-eid r))]})
   (query sqlite-conn
          "SELECT a.eid, a.key, a.name, a.description, a.ability_type, a.cost,
                  g.eid AS game_eid
             FROM ability a
             JOIN game g ON a.game_id = g.id
            WHERE a.deleted_at IS NULL")))

(defn- load-attributes
  [sqlite-conn]
  (mapv
   (fn [r]
     {:attribute/eid         (uuid (:eid r))
      :attribute/key         (:key r)
      :attribute/name        (:name r)
      :attribute/description (:description r)
      :attribute/game        [:game/eid (uuid (:game-eid r))]})
   (query sqlite-conn
          "SELECT a.eid, a.key, a.name, a.description, g.eid AS game_eid
             FROM attribute a
             JOIN game g ON a.game_id = g.id
            WHERE a.deleted_at IS NULL")))

(defn- load-items
  [sqlite-conn]
  (mapv
   (fn [r]
     (cond-> {:item/eid      (uuid (:eid r))
              :item/key      (:key r)
              :item/name     (:name r)
              :item/category (:category r)
              :item/cost     (:cost r)
              :item/game     [:game/eid (uuid (:game-eid r))]}
       (:icon-key r) (assoc :item/icon-key (:icon-key r))))
   (query sqlite-conn
          "SELECT i.eid, i.key, i.name, i.category, i.cost, i.icon_key,
                  g.eid AS game_eid
             FROM item i
             JOIN game g ON i.game_id = g.id
            WHERE i.deleted_at IS NULL")))

(defn- load-mounts
  [sqlite-conn]
  (mapv
   (fn [r]
     (cond-> {:mount/eid  (uuid (:eid r))
              :mount/key  (:key r)
              :mount/name (:name r)
              :mount/game [:game/eid (uuid (:game-eid r))]}
       (:icon-key r) (assoc :mount/icon-key (:icon-key r))))
   (query sqlite-conn
          "SELECT m.eid, m.key, m.name, m.icon_key, g.eid AS game_eid
             FROM mount m
             JOIN game g ON m.game_id = g.id
            WHERE m.deleted_at IS NULL")))

(defn- load-unit-items
  "Junction table without an eid in SQLite — derive one from
   (unit-eid + item-eid) so re-runs upsert."
  [sqlite-conn]
  (mapv
   (fn [r]
     (let [unit-eid (uuid (:unit-eid r))
           item-eid (uuid (:item-eid r))]
       {:unit-item/eid  (derived-uuid "unit-item" unit-eid item-eid)
        :unit-item/unit [:unit/eid unit-eid]
        :unit-item/item [:item/eid item-eid]}))
   (query sqlite-conn
          "SELECT u.eid AS unit_eid, i.eid AS item_eid
             FROM unit_item ui
             JOIN unit u ON ui.unit_id = u.id
             JOIN item i ON ui.item_id = i.id
            WHERE ui.deleted_at IS NULL")))

(defn- load-unit-mounts
  "Junction table without an eid in SQLite — derive one from
   (unit-eid + mount-eid)."
  [sqlite-conn]
  (mapv
   (fn [r]
     (let [unit-eid  (uuid (:unit-eid r))
           mount-eid (uuid (:mount-eid r))]
       (cond-> {:unit-mount/eid   (derived-uuid "unit-mount" unit-eid mount-eid)
                :unit-mount/unit  [:unit/eid unit-eid]
                :unit-mount/mount [:mount/eid mount-eid]
                :unit-mount/cost  (:cost r)}
         (:stats-override r)
         (assoc :unit-mount/stats-override (:stats-override r))
         (and (:granted-ability-keys r) (not (str/blank? (:granted-ability-keys r))))
         (assoc :unit-mount/granted-ability-keys
                (str/split (:granted-ability-keys r) #",")))))
   (query sqlite-conn
          "SELECT u.eid AS unit_eid, m.eid AS mount_eid,
                  um.cost, um.stats_override, um.granted_ability_keys
             FROM unit_mount um
             JOIN unit u ON um.unit_id = u.id
             JOIN mount m ON um.mount_id = m.id
            WHERE um.deleted_at IS NULL")))

(defn- load-unit-level-costs
  [sqlite-conn]
  (mapv
   (fn [r]
     {:unit-level-cost/level           (:level r)
      :unit-level-cost/fixed-cost      (:fixed-cost r)
      :unit-level-cost/cost-multiplier (:cost-multiplier r)
      :unit-level-cost/fatigue         (:fatigue r)
      :unit-level-cost/melee-cp        (:melee-cp r)
      :unit-level-cost/missile-cp      (:missile-cp r)})
   (query sqlite-conn
          "SELECT level, fixed_cost, cost_multiplier, fatigue, melee_cp, missile_cp
             FROM unit_level_cost")))

;;; ─── Unit + unit-statistics decomposition ─────────────────────────────────

(def ^:private known-statistics-keys
  "Top-level keys in the unit_statistics JSON that map to first-class
   `:unit-statistics/*` attrs; everything else becomes a `:unit-stat`
   sub-entity."
  #{"health" "barrier" "abilities" "draftable-spells" "draftable-abilities"
    "attributes" "equipment" "mounts"})

(defn- ->unit-stat-tx
  [unit-statistics-eid stat-key raw-value]
  {:unit-stat/eid   (derived-uuid "unit-stat" unit-statistics-eid stat-key)
   :unit-stat/key   stat-key
   :unit-stat/value (str raw-value)})

(defn- ->unit-statistics-tx
  "Convert a unit's JSON stats blob to a `:unit-statistics` map ready to
   transact. `:stats` is filled with `:unit-stat` sub-entities derived from
   the unknown dynamic engine keys."
  [{:keys [unit-statistics-eid patch-eid stats-json]}]
  (let [decoded (jsonista/read-value stats-json object-mapper)]
    (cond-> {:unit-statistics/eid   unit-statistics-eid
             :unit-statistics/patch [:patch/eid patch-eid]}
      (get decoded "health")
      (assoc :unit-statistics/health (long (get decoded "health")))

      (and (get decoded "barrier") (pos? (get decoded "barrier")))
      (assoc :unit-statistics/barrier (long (get decoded "barrier")))

      (seq (get decoded "abilities"))
      (assoc :unit-statistics/abilities (vec (get decoded "abilities")))

      (seq (get decoded "draftable-spells"))
      (assoc :unit-statistics/draftable-spell-keys
             (mapv #(get % "key") (get decoded "draftable-spells")))

      (seq (get decoded "draftable-abilities"))
      (assoc :unit-statistics/draftable-ability-keys
             (vec (get decoded "draftable-abilities")))

      (seq (get decoded "attributes"))
      (assoc :unit-statistics/attributes (vec (get decoded "attributes")))

      (seq (get decoded "equipment"))
      (assoc :unit-statistics/equipment (pr-str (get decoded "equipment")))

      (seq (remove (fn [[k _]] (contains? known-statistics-keys k)) decoded))
      (assoc :unit-statistics/stats
             (->> decoded
                  (remove (fn [[k _]] (contains? known-statistics-keys k)))
                  (mapv (fn [[k v]] (->unit-stat-tx unit-statistics-eid k v))))))))

(defn- load-units+stats
  "Returns `{:units [...] :unit-statistics [...]}`. Units are transacted
   first; the `:unit/unit-statistics` ref is added in a second transaction
   once `:unit-statistics` entities exist."
  [sqlite-conn patch-eid]
  (let [unit-rows (query sqlite-conn
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
                           WHERE u.deleted_at IS NULL")]
    {:units
     (mapv
      (fn [r]
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
      unit-rows)

     :unit-statistics
     (mapv
      (fn [r]
        (let [unit-eid            (uuid (:eid r))
              unit-statistics-eid (derived-uuid "unit-statistics" patch-eid unit-eid)]
          {:unit-eid            unit-eid
           :unit-statistics-eid unit-statistics-eid
           :patch-eid           patch-eid
           :stats-json          (:unit-statistics r)}))
      unit-rows)}))

(defn- ->unit-statistics-links
  "After unit-statistics entities are transacted, attach them to their
   units via the cardinality-many `:unit/unit-statistics` ref."
  [unit-statistics]
  (mapv
   (fn [{:keys [unit-eid unit-statistics-eid]}]
     {:unit/eid             unit-eid
      :unit/unit-statistics [:unit-statistics/eid unit-statistics-eid]})
   unit-statistics))

;;; ─── Orchestrator ─────────────────────────────────────────────────────────

(defn- transact-batch!
  [datalog-conn label tx-data]
  (when (seq tx-data)
    (datalog/transact! datalog-conn tx-data))
  (println (format "  %-22s %d entities" label (count tx-data))))

(defn migrate!
  "Read all game-domain data from SQLite (`db-spec`) and transact it into
   Datalevin (`datalog-conn`). Idempotent on re-run.

   `opts`:
     :patch-version  — string version label for the unit-statistics
                       snapshot (default `\"current\"`)."
  ([db-spec datalog-conn]
   (migrate! db-spec datalog-conn {}))
  ([db-spec datalog-conn {:keys [patch-version] :or {patch-version "current"}}]
   (with-open [sqlite-conn (jdbc/get-connection db-spec)]
     (let [patch-eid                       (derived-uuid "patch" patch-version)
           {:keys [units unit-statistics]} (load-units+stats sqlite-conn patch-eid)]
       (println "Migrating game-domain data SQLite → Datalevin")
       (println (format "  patch:                 %s (%s)" patch-version patch-eid))

       ;; Phase 1: independent entities (no FKs to other game-domain rows).
       (transact-batch! datalog-conn "games"          (load-games sqlite-conn))
       (transact-batch! datalog-conn "social media"   (load-social-media-platforms sqlite-conn))
       (transact-batch! datalog-conn "unit level cost" (load-unit-level-costs sqlite-conn))
       (transact-batch! datalog-conn "patch"
                        [{:patch/eid         patch-eid
                          :patch/version     patch-version
                          :patch/released-at (Date/from (Instant/now))}])

       ;; Phase 2: per-game lookups (games must exist first).
       (transact-batch! datalog-conn "game social links" (load-game-social-links sqlite-conn))
       (transact-batch! datalog-conn "unit types"        (load-unit-types sqlite-conn))
       (transact-batch! datalog-conn "unit categories"   (load-unit-categories sqlite-conn))
       (transact-batch! datalog-conn "factions"          (load-factions sqlite-conn))
       (transact-batch! datalog-conn "game modes"        (load-game-modes sqlite-conn))
       (transact-batch! datalog-conn "lores"             (load-lores sqlite-conn))
       (transact-batch! datalog-conn "spells"            (load-spells sqlite-conn))
       (transact-batch! datalog-conn "abilities"         (load-abilities sqlite-conn))
       (transact-batch! datalog-conn "attributes"        (load-attributes sqlite-conn))
       (transact-batch! datalog-conn "items"             (load-items sqlite-conn))
       (transact-batch! datalog-conn "mounts"            (load-mounts sqlite-conn))

       ;; Phase 3: refs into phase-2 entities.
       (transact-batch! datalog-conn "subfactions"       (load-subfactions sqlite-conn))
       (transact-batch! datalog-conn "spell lores"       (load-spell-lores sqlite-conn))

       ;; Phase 4: units (need factions, unit-types, unit-categories).
       (transact-batch! datalog-conn "units" units)

       ;; Phase 5: junctions hanging off units.
       (transact-batch! datalog-conn "unit items"        (load-unit-items sqlite-conn))
       (transact-batch! datalog-conn "unit mounts"       (load-unit-mounts sqlite-conn))

       ;; Phase 6: unit-statistics (decomposed from the JSON blob), plus the
       ;; back-link onto units.
       (transact-batch! datalog-conn "unit statistics"
                        (mapv ->unit-statistics-tx unit-statistics))
       (transact-batch! datalog-conn "→ unit stats link"
                        (->unit-statistics-links unit-statistics))

       (println "Migration complete.")))))

;;; ─── REPL convenience ────────────────────────────────────────────────────

(defn seed!
  "REPL-friendly entry point. Pulls the default SQLite and Datalevin
   connections from `rts-api.db` / `rts-api.datalog` so callers don't have
   to thread them through. Opens its own Datalevin connection — do not
   call while the dev system is running, or pass an existing conn to
   [[migrate!]] directly."
  ([] (seed! {}))
  ([opts]
   (let [conn (datalog/get-conn rts-datalog/dir rda/datalog-schema)]
     (try
       (migrate! rts-db/db conn opts)
       (finally
         (datalog/close conn))))))

(comment
  ;; Run after seed-sqlite! to mirror game data into Datalevin.
  (seed!)
  (seed! {:patch-version "5.3.4"}))
