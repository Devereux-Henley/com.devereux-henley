(ns com.devereux-henley.rts-data-access.query.datalog.game
  "Per-template Datalevin pull/q queries for the game domain. Each public
  fn matches one template's data shape (game-collection, game detail,
  faction-collection, faction detail, unit-collection, unit detail,
  game-selector). Handlers reach these through `rts-data-access.contract`
  alongside the SQLite-era `query/game.clj` fns; the SQL path lives until
  `rts-khy` retires it.

  Result maps use unqualified keys (`:eid`, `:name`, `:game-eid`, …) so
  the existing resource schemas in `rts-domain` and Selmer templates
  render unchanged. Foreign-key refs are pulled as `{<ns>/eid uuid}`
  sub-maps and flattened to `:<thing>-eid` to match the SQLite-era entity
  shape.

  Each public fn snapshots the current db at entry (`(dl/db conn)`) and
  uses it for the single pull/q it issues. The snapshot is cheap (one
  atomic deref) and binds the query to a consistent point-in-time view —
  later transacts don't change the result mid-fn."
  (:require
   [com.devereux-henley.datalog.contract :as dl]))

;; ─── Pull patterns ─────────────────────────────────────────────────────────

(def ^:private game-pattern
  [:game/eid :game/name :game/description])

(def ^:private faction-pattern
  [:faction/eid :faction/name :faction/key :faction/description
   {:faction/game [:game/eid]}])

(def ^:private unit-summary-pattern
  "Shape for unit lists / cards. Includes only the fields list templates and
   the resource collection schemas read; full statline lives on the detail
   pattern."
  [:unit/eid :unit/key :unit/name :unit/family-name :unit/description
   :unit/mark :unit/lore :unit/is-unique
   {:unit/game [:game/eid]}
   {:unit/faction [:faction/eid]}
   {:unit/unit-type [:unit-type/eid :unit-type/name]}
   {:unit/unit-category [:unit-category/eid :unit-category/name]}
   {:unit-statistics/_unit [:unit-statistics/cost
                            {:unit-statistics/patch [:patch/released-at]}]}])

(def ^:private unit-detail-pattern
  "Adds the full statistics document for the unit detail view."
  [:unit/eid :unit/key :unit/name :unit/family-name :unit/description
   :unit/mark :unit/lore :unit/is-unique
   {:unit/game [:game/eid]}
   {:unit/faction [:faction/eid]}
   {:unit/unit-type [:unit-type/eid :unit-type/name]}
   {:unit/unit-category [:unit-category/eid :unit-category/name]}
   {:unit-statistics/_unit [:unit-statistics/cost :unit-statistics/data
                            {:unit-statistics/patch [:patch/released-at]}]}])

(def ^:private game-mode-pattern
  [:game-mode/eid :game-mode/name :game-mode/description :game-mode/draft-value
   :game-mode/player-count :game-mode/reinforcement-value
   :game-mode/reinforcements-enabled
   {:game-mode/game [:game/eid]}])

(def ^:private social-link-pattern
  [:game-social-link/eid :game-social-link/url
   {:game-social-link/game [:game/eid]}
   {:game-social-link/platform [:social-media-platform/eid
                                :social-media-platform/name
                                :social-media-platform/description
                                :social-media-platform/platform-url]}])

(def ^:private mount-pattern
  [:unit-mount/cost :unit-mount/stats-override :unit-mount/granted-ability-keys
   {:unit-mount/mount [:mount/eid :mount/key :mount/name :mount/icon-key]}])

(def ^:private item-pattern
  [:item/eid :item/key :item/name :item/category :item/cost :item/icon-key])

(def ^:private spell-pattern
  [:spell/eid :spell/key :spell/name :spell/mana-cost :spell/cost])

(def ^:private ability-pattern
  [:ability/eid :ability/key :ability/name :ability/description :ability/cost])

;; ─── Result builders ───────────────────────────────────────────────────────

(defn- latest-stats
  "Pick the unit-statistics record with the most recent patch released-at
  from a `:unit-statistics/_unit` reverse-ref pull result."
  [stats-rows]
  (->> stats-rows
       (sort-by (comp :patch/released-at :unit-statistics/patch))
       last))

(defn- ->game
  [m]
  (when m
    {:type        :game/game
     :eid         (:game/eid m)
     :name        (:game/name m)
     :description (:game/description m)}))

(defn- ->faction
  [m]
  (when m
    {:type        :game/faction
     :eid         (:faction/eid m)
     :name        (:faction/name m)
     :key         (:faction/key m)
     :description (:faction/description m)
     :game-eid    (some-> m :faction/game :game/eid)}))

(defn- ->unit-row
  "Build the row shape `unit-entity` consumers expect from a pull result.
  `stats-rows` is the `:unit-statistics/_unit` vector; the latest patch's
  cost lands on `:cost`, and `:unit-statistics` carries the decoded doc
  (only populated by `->unit-detail`)."
  [m {:keys [include-data?]}]
  (when m
    (let [stats                         (latest-stats (:unit-statistics/_unit m))
          {ut-eid  :unit-type/eid
           ut-name :unit-type/name}     (:unit/unit-type m)
          {uc-eid  :unit-category/eid
           uc-name :unit-category/name} (:unit/unit-category m)]
      (cond-> {:type               :game/unit
               :eid                (:unit/eid m)
               :key                (:unit/key m)
               :name               (:unit/name m)
               :family-name        (:unit/family-name m)
               :description        (:unit/description m)
               :mark               (some-> (:unit/mark m) name)
               :lore               (:unit/lore m)
               :is-unique          (if (:unit/is-unique m) 1 0)
               :game-eid           (some-> m :unit/game :game/eid)
               :faction-eid        (some-> m :unit/faction :faction/eid)
               :unit-type-eid      ut-eid
               :unit-type-name     ut-name
               :unit-category-eid  uc-eid
               :unit-category-name uc-name
               :cost               (:unit-statistics/cost stats)}
        include-data? (assoc :unit-statistics (:unit-statistics/data stats))))))

(defn- ->unit-summary [m] (->unit-row m {:include-data? false}))
(defn- ->unit-detail  [m] (->unit-row m {:include-data? true}))

(defn- ->game-mode
  [m]
  (when m
    {:eid                    (:game-mode/eid m)
     :name                   (:game-mode/name m)
     :description            (:game-mode/description m)
     :draft-value            (:game-mode/draft-value m)
     :player-count           (:game-mode/player-count m)
     :reinforcement-value    (:game-mode/reinforcement-value m)
     :reinforcements-enabled (:game-mode/reinforcements-enabled m)
     :game-eid               (some-> m :game-mode/game :game/eid)}))

(defn- ->social
  [m]
  (when m
    (let [{p-eid  :social-media-platform/eid
           p-name :social-media-platform/name
           p-desc :social-media-platform/description
           p-url  :social-media-platform/platform-url} (:game-social-link/platform m)]
      {:type                      :game/social
       :eid                       (:game-social-link/eid m)
       :url                       (:game-social-link/url m)
       :game-eid                  (some-> m :game-social-link/game :game/eid)
       :social-media-platform-eid p-eid
       :platform-name             p-name
       :platform-description      p-desc
       :platform-url              p-url})))

(defn- ->mount
  [m]
  (when m
    (let [mount   (:unit-mount/mount m)
          granted (:unit-mount/granted-ability-keys m)]
      {:eid                  (:mount/eid mount)
       :key                  (:mount/key mount)
       :name                 (:mount/name mount)
       :icon-key             (:mount/icon-key mount)
       :cost                 (:unit-mount/cost m)
       :stats-override       (:unit-mount/stats-override m)
       :granted-ability-keys (when (seq granted) (vec granted))})))

(defn- ->item
  [m]
  (when m
    {:eid      (:item/eid m)
     :key      (:item/key m)
     :name     (:item/name m)
     :category (:item/category m)
     :cost     (:item/cost m)
     :icon-key (:item/icon-key m)}))

(defn- ->spell
  [m]
  (when m
    {:eid       (:spell/eid m)
     :key       (:spell/key m)
     :name      (:spell/name m)
     :mana-cost (:spell/mana-cost m)
     :cost      (:spell/cost m)}))

(defn- ->ability
  [m]
  (when m
    {:eid         (:ability/eid m)
     :key         (:ability/key m)
     :name        (:ability/name m)
     :description (:ability/description m)
     :cost        (:ability/cost m)}))

;; ─── Game queries ──────────────────────────────────────────────────────────

(defn games
  "All games. Drives the `game-collection` template and `game-selector`."
  [conn]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?g pattern) ...]
                 :in $ pattern
                 :where [?g :game/eid]]
               db game-pattern)
         (mapv ->game)
         (sort-by :name)
         vec)))

(defn game-by-eid
  "Single game for the `game` detail template. Returns nil when not found."
  [conn eid]
  (->game (dl/pull (dl/db conn) game-pattern (dl/lookup-ref :game/eid eid))))

;; ─── Faction queries ───────────────────────────────────────────────────────

(defn factions-for-game
  "Factions for a game, sorted by name. Drives `faction-collection`."
  [conn game-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?f pattern) ...]
                 :in $ pattern ?game-eid
                 :where
                 [?g :game/eid ?game-eid]
                 [?f :faction/game ?g]]
               db faction-pattern game-eid)
         (mapv ->faction)
         (sort-by :name)
         vec)))

(defn factions
  "Every faction in the system, sorted by name."
  [conn]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?f pattern) ...]
                 :in $ pattern
                 :where [?f :faction/eid]]
               db faction-pattern)
         (mapv ->faction)
         (sort-by :name)
         vec)))

(defn faction-by-eid
  "Single faction for the `faction` detail template."
  [conn eid]
  (->faction (dl/pull (dl/db conn) faction-pattern (dl/lookup-ref :faction/eid eid))))

;; ─── Unit queries ──────────────────────────────────────────────────────────

(defn units-for-faction
  "Unit summary rows for a faction, sorted by `(unit-category-name, name)`."
  [conn faction-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?u pattern) ...]
                 :in $ pattern ?faction-eid
                 :where
                 [?f :faction/eid ?faction-eid]
                 [?u :unit/faction ?f]]
               db unit-summary-pattern faction-eid)
         (mapv ->unit-summary)
         (sort-by (juxt :unit-category-name :name))
         vec)))

(defn units-for-game
  "Unit summary rows for a game."
  [conn game-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?u pattern) ...]
                 :in $ pattern ?game-eid
                 :where
                 [?g :game/eid ?game-eid]
                 [?u :unit/game ?g]]
               db unit-summary-pattern game-eid)
         (mapv ->unit-summary)
         (sort-by (juxt :unit-category-name :name))
         vec)))

(defn units
  "Every unit summary in the system."
  [conn]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?u pattern) ...]
                 :in $ pattern
                 :where [?u :unit/eid]]
               db unit-summary-pattern)
         (mapv ->unit-summary)
         (sort-by (juxt :unit-category-name :name))
         vec)))

(defn unit-by-eid
  "Full unit row including the decoded `:unit-statistics` document."
  [conn eid]
  (->unit-detail (dl/pull (dl/db conn) unit-detail-pattern (dl/lookup-ref :unit/eid eid))))

;; ─── Embed queries (game detail + faction detail enrichments) ──────────────

(defn game-mode-by-eid
  "Single game-mode by eid."
  [conn eid]
  (->game-mode (dl/pull (dl/db conn) game-mode-pattern (dl/lookup-ref :game-mode/eid eid))))

(defn game-modes-for-game
  [conn game-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?gm pattern) ...]
                 :in $ pattern ?game-eid
                 :where
                 [?g :game/eid ?game-eid]
                 [?gm :game-mode/game ?g]]
               db game-mode-pattern game-eid)
         (mapv ->game-mode)
         (sort-by :name)
         vec)))

(defn socials-for-game
  [conn game-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?s pattern) ...]
                 :in $ pattern ?game-eid
                 :where
                 [?g :game/eid ?game-eid]
                 [?s :game-social-link/game ?g]]
               db social-link-pattern game-eid)
         (mapv ->social)
         (sort-by :platform-name)
         vec)))

;; ─── Unit enrichments (detail page) ────────────────────────────────────────

(defn mounts-for-unit
  [conn unit-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?um pattern) ...]
                 :in $ pattern ?unit-eid
                 :where
                 [?u :unit/eid ?unit-eid]
                 [?um :unit-mount/unit ?u]]
               db mount-pattern unit-eid)
         (mapv ->mount)
         (sort-by :name)
         vec)))

(defn items-for-unit
  [conn unit-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?i pattern) ...]
                 :in $ pattern ?unit-eid
                 :where
                 [?u :unit/eid ?unit-eid]
                 [?ui :unit-item/unit ?u]
                 [?ui :unit-item/item ?i]]
               db item-pattern unit-eid)
         (mapv ->item)
         (sort-by :name)
         vec)))

(defn spells-by-keys
  "Resolve a seq of spell keys into a `{key spell-resource}` map."
  [conn spell-keys]
  (when (seq spell-keys)
    (let [results (dl/q '[:find [(pull ?s pattern) ...]
                          :in $ pattern [?key ...]
                          :where [?s :spell/key ?key]]
                        (dl/db conn) spell-pattern (vec spell-keys))]
      (into {} (map (fn [m] [(:spell/key m) (->spell m)])) results))))

(defn spells-for-lore
  "All spells assigned to a given lore key (resolved via spell-lore)."
  [conn lore-key]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?s pattern) ...]
                 :in $ pattern ?lore-key
                 :where
                 [?l :lore/key ?lore-key]
                 [?sl :spell-lore/lore ?l]
                 [?sl :spell-lore/spell ?s]]
               db spell-pattern lore-key)
         (mapv ->spell)
         (sort-by :name)
         vec)))

(defn abilities-by-keys
  "Resolve a seq of ability keys into a `{key ability-resource}` map."
  [conn ability-keys]
  (when (seq ability-keys)
    (let [results (dl/q '[:find [(pull ?a pattern) ...]
                          :in $ pattern [?key ...]
                          :where [?a :ability/key ?key]]
                        (dl/db conn) ability-pattern (vec ability-keys))]
      (into {} (map (fn [m] [(:ability/key m) (->ability m)])) results))))

;; ─── Misc draft-handler dependencies ──────────────────────────────────────

(def ^:private unit-level-cost-pattern
  [:unit-level-cost/level :unit-level-cost/fixed-cost
   :unit-level-cost/cost-multiplier :unit-level-cost/fatigue
   :unit-level-cost/melee-cp :unit-level-cost/missile-cp])

(defn- ->unit-level-cost [m]
  (when m
    {:level           (:unit-level-cost/level m)
     :fixed-cost      (:unit-level-cost/fixed-cost m)
     :cost-multiplier (:unit-level-cost/cost-multiplier m)
     :fatigue         (:unit-level-cost/fatigue m)
     :melee-cp        (:unit-level-cost/melee-cp m)
     :missile-cp      (:unit-level-cost/missile-cp m)}))

(defn unit-level-costs
  "All veteran-rank cost rows as a sorted `{level -> row}` map. Matches
  the shape the draft handler's level-cost lookup expects."
  [conn]
  (into (sorted-map)
        (map (juxt :level identity))
        (->> (dl/q '[:find [(pull ?c pattern) ...]
                     :in $ pattern
                     :where [?c :unit-level-cost/level]]
                   (dl/db conn) unit-level-cost-pattern)
             (map ->unit-level-cost))))

(def ^:private family-variant-pattern
  [:unit/eid :unit/name :unit/mark :unit/lore
   {:unit-statistics/_unit [:unit-statistics/cost
                            {:unit-statistics/patch [:patch/released-at]}]}])

(defn- ->family-variant
  [m lore-key->name]
  (when m
    (let [stats (latest-stats (:unit-statistics/_unit m))
          lore  (:unit/lore m)]
      {:eid       (:unit/eid m)
       :name      (:unit/name m)
       :mark      (some-> (:unit/mark m) name)
       :lore      lore
       :lore-name (get lore-key->name lore)
       :cost      (:unit-statistics/cost stats)})))

(defn family-variants-by-eid
  "Every unit row sharing the family-name + faction of the given unit.
  Single-variant families come back with one row; the draft unit panel
  uses the count to decide whether to render the mark / lore selectors."
  [conn unit-eid]
  (let [db          (dl/db conn)
        seed        (dl/pull db
                             '[:unit/family-name
                               {:unit/faction [:db/id]}]
                             (dl/lookup-ref :unit/eid unit-eid))
        family-name (:unit/family-name seed)
        faction-id  (some-> seed :unit/faction :db/id)]
    (when (and family-name faction-id)
      (let [variants  (dl/q '[:find [(pull ?u pattern) ...]
                              :in $ pattern ?family-name ?faction-id
                              :where
                              [?u :unit/family-name ?family-name]
                              [?u :unit/faction ?faction-id]]
                            db family-variant-pattern family-name faction-id)
            lore-keys (->> variants (keep :unit/lore) set)
            lore-rows (when (seq lore-keys)
                        (dl/q '[:find ?key ?name
                                :in $ [?key ...]
                                :where
                                [?l :lore/key ?key]
                                [?l :lore/name ?name]]
                              db (vec lore-keys)))
            key->name (into {} lore-rows)]
        (->> variants
             (mapv #(->family-variant % key->name))
             (sort-by (juxt :name (fn [v] (or (:mark v) ""))
                            (fn [v] (or (:lore v) ""))))
             vec)))))
