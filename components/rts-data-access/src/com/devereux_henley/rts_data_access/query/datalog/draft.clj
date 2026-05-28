(ns com.devereux-henley.rts-data-access.query.datalog.draft
  "Datalevin reads and tx-builders for the draft domain. Mirrors the
  game-domain pattern (`query.datalog.game`) — each public fn snapshots
  the current db once at entry, runs a single pull/q, and returns the
  flat unqualified-key shape the existing `rts-domain` handlers expect
  so resource schemas + Selmer templates render unchanged.

  Mutations are split into pure tx-builders (compute the tx-data) and
  transact entry points (apply via `datalog.contract/transact!`). The
  split keeps validation/business logic in `rts-domain` free of conn
  argument plumbing while letting the domain layer compose a single tx
  per mutation."
  (:require
   [com.devereux-henley.datalog.contract :as dl])
  (:import
   [java.time ZoneId]
   [java.time.format DateTimeFormatter]
   [java.util Date]))

;;; ─── Pull patterns ─────────────────────────────────────────────────────────

(def ^:private draft-pattern
  [:draft/eid :draft/name :draft/player-sub :draft/version
   :draft/created-by-sub :draft/created-at :draft/updated-at
   {:draft/game-mode [:game-mode/eid {:game-mode/game [:game/eid]}]}
   {:draft/faction [:faction/eid :faction/name {:faction/game [:game/eid]}]}])

(def ^:private entry-pattern
  [:draft-entry/eid :draft-entry/section :draft-entry/ordinal
   :draft-entry/mount :draft-entry/lore :draft-entry/level
   :draft-entry/abilities :draft-entry/spells :draft-entry/items
   :draft-entry/total-cost :draft-entry/engine-cost
   {:draft-entry/unit [:unit/eid]}])

;;; ─── Display helpers ──────────────────────────────────────────────────────

(def ^:private mm-dd-yyyy
  (DateTimeFormatter/ofPattern "MM/dd/yyyy"))

(defn- ->display
  "Render a `java.util.Date` (Datalevin's instant return type) as
  `MM/dd/yyyy` for templates that surface a created/updated string."
  [d]
  (when d
    (-> (.toInstant ^Date d)
        (.atZone (ZoneId/systemDefault))
        .toLocalDate
        (.format mm-dd-yyyy))))

;;; ─── Result builders ──────────────────────────────────────────────────────

(defn- ->draft
  "Flatten a draft pull result into the unqualified-key shape the
  SQLite-era handlers expect: ref sub-maps become flat `*-eid` fields,
  date display strings get added, and ordering metadata stays out of the
  draft itself (entries are returned via `draft-state`)."
  [m]
  (when m
    (let [created-at (:draft/created-at m)
          updated-at (:draft/updated-at m)
          faction    (:draft/faction m)
          game-mode  (:draft/game-mode m)]
      (cond-> {:eid                (:draft/eid m)
               :name               (:draft/name m)
               :player-sub         (:draft/player-sub m)
               :version            (:draft/version m)
               :game-mode-eid      (some-> game-mode :game-mode/eid)
               :faction-eid        (some-> faction :faction/eid)
               :faction-name       (some-> faction :faction/name)
               :game-eid           (some-> game-mode :game-mode/game :game/eid)
               :created-at         created-at
               :updated-at         updated-at
               :created-at-display (->display created-at)
               :updated-at-display (->display updated-at)}
        (:draft/created-by-sub m) (assoc :created-by-sub (:draft/created-by-sub m))))))

(defn- ->entry
  "Flatten a draft-entry pull result into the JSON-state-shaped map the
  existing draft handlers operate on: `{:entry-eid :unit-eid :mount …
  :section :ordinal}`. Cardinality-many vectors come back from datalevin
  as sets; sort to keep template output stable."
  [m]
  (when m
    (let [maybe-vec (fn [coll] (when (seq coll) (vec (sort coll))))]
      (cond-> {:entry-eid (:draft-entry/eid m)
               :unit-eid  (some-> m :draft-entry/unit :unit/eid)
               :section   (:draft-entry/section m)
               :ordinal   (:draft-entry/ordinal m)
               :level     (or (:draft-entry/level m) 0)}
        (:draft-entry/mount m)        (assoc :mount       (:draft-entry/mount m))
        (:draft-entry/lore m)         (assoc :lore        (:draft-entry/lore m))
        (:draft-entry/total-cost m)   (assoc :total-cost  (:draft-entry/total-cost m))
        (:draft-entry/engine-cost m)  (assoc :engine-cost (:draft-entry/engine-cost m))
        (seq (:draft-entry/abilities m)) (assoc :abilities (maybe-vec (:draft-entry/abilities m)))
        (seq (:draft-entry/spells m))    (assoc :spells    (maybe-vec (:draft-entry/spells m)))
        (seq (:draft-entry/items m))     (assoc :items     (maybe-vec (:draft-entry/items m)))))))

(defn- entries->state
  "Group a vector of entry maps by section and sort each section by
  ordinal, producing the `{:main […] :reinforcements […]}` shape the
  existing draft handlers (and the rules engine) operate on."
  [entries]
  (let [groups (group-by :section entries)]
    {:main           (vec (sort-by :ordinal (get groups :main [])))
     :reinforcements (vec (sort-by :ordinal (get groups :reinforcements [])))}))

;;; ─── Reads ────────────────────────────────────────────────────────────────

(defn draft-by-eid
  "Single draft, flat shape matching the SQLite `draft-entity` row.
  Returns nil when the draft doesn't exist so callers can distinguish
  missing from present."
  [conn eid]
  (->draft (dl/pull (dl/db conn) draft-pattern (dl/lookup-ref :draft/eid eid))))

(defn drafts-for-player
  "All drafts a player owns, sorted by `(updated-at desc, eid)` to mirror
  the SQLite-era ORDER BY."
  [conn player-sub]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?d pattern) ...]
                 :in $ pattern ?player-sub
                 :where [?d :draft/player-sub ?player-sub]]
               db draft-pattern player-sub)
         (mapv ->draft)
         (sort-by (juxt (comp - (fnil #(.getTime ^Date %) (Date. 0)) :updated-at) :eid))
         vec)))

(defn drafts-for-player-by-game
  "Drafts a player owns scoped to a specific game (matched through the
  game-mode's parent game ref). Sorted updated-at desc."
  [conn player-sub game-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?d pattern) ...]
                 :in $ pattern ?player-sub ?game-eid
                 :where
                 [?d :draft/player-sub ?player-sub]
                 [?d :draft/game-mode ?gm]
                 [?gm :game-mode/game ?g]
                 [?g :game/eid ?game-eid]]
               db draft-pattern player-sub game-eid)
         (mapv ->draft)
         (sort-by (juxt (comp - (fnil #(.getTime ^Date %) (Date. 0)) :updated-at) :eid))
         vec)))

(defn draft-state-by-eid
  "Returns `{:main [entry …] :reinforcements [entry …]}` for the draft —
  the JSON-state shape the existing handlers and rules engine consume.
  Each entry has `:entry-eid :unit-eid :section :ordinal` plus any
  optional selection attrs that are set."
  [conn draft-eid]
  (let [pulled (dl/pull (dl/db conn)
                        '[{:draft/entries [:draft-entry/eid :draft-entry/section
                                           :draft-entry/ordinal :draft-entry/mount
                                           :draft-entry/lore :draft-entry/level
                                           :draft-entry/abilities :draft-entry/spells
                                           :draft-entry/items :draft-entry/total-cost
                                           :draft-entry/engine-cost
                                           {:draft-entry/unit [:unit/eid]}]}]
                        (dl/lookup-ref :draft/eid draft-eid))]
    (entries->state (mapv ->entry (:draft/entries pulled)))))

(defn draft-entry-by-eid
  "Single entry in flat-state shape, or nil when no entry has that eid."
  [conn entry-eid]
  (->entry (dl/pull (dl/db conn) entry-pattern (dl/lookup-ref :draft-entry/eid entry-eid))))

(defn draft-entry-section-and-ordinal
  "Look up `{:section :ordinal}` for an entry. Used by validation to know
  which section an entry being updated lives in without re-reading the
  full state."
  [conn entry-eid]
  (let [m (dl/pull (dl/db conn) [:draft-entry/section :draft-entry/ordinal]
                   (dl/lookup-ref :draft-entry/eid entry-eid))]
    (when (:draft-entry/section m)
      {:section (:draft-entry/section m)
       :ordinal (:draft-entry/ordinal m)})))

;;; ─── Tx-builders + transact entry points ──────────────────────────────────

(defn- now-date [] (Date.))

(defn create-draft!
  "Transact a new draft. `spec` carries `:eid :name :player-sub
  :game-mode-eid :faction-eid :created-by-sub` (everything else
  derived). `:created-by-sub` defaults to `:player-sub` when the
  caller doesn't differentiate. Returns the freshly-created flat
  draft map."
  [conn {:keys [eid name player-sub game-mode-eid faction-eid created-by-sub]}]
  (let [created-at (now-date)]
    (dl/transact!
     conn
     [(cond-> {:draft/eid            eid
               :draft/player-sub     player-sub
               :draft/game-mode      [:game-mode/eid game-mode-eid]
               :draft/faction        [:faction/eid faction-eid]
               :draft/version        1
               :draft/created-by-sub (or created-by-sub player-sub)
               :draft/created-at     created-at
               :draft/updated-at     created-at}
        name (assoc :draft/name name))])
    (draft-by-eid conn eid)))

(defn update-draft-name!
  "Update the draft's mutable `:name` field (and bump version + updated-at).
  Returns the refreshed flat draft. Passing `nil` for `:name` retracts
  the current value so the faction+date default renders again."
  [conn draft-eid name]
  (let [db            (dl/db conn)
        current       (dl/pull db [:draft/version :draft/name]
                               (dl/lookup-ref :draft/eid draft-eid))
        next-version  (inc (or (:draft/version current) 1))
        existing-name (:draft/name current)
        retract-ops   (when (and existing-name (nil? name))
                        [[:db/retract [:draft/eid draft-eid] :draft/name existing-name]])
        upsert        (cond-> {:draft/eid        draft-eid
                               :draft/version    next-version
                               :draft/updated-at (now-date)}
                        name (assoc :draft/name name))]
    (dl/transact! conn (cons upsert retract-ops))
    (draft-by-eid conn draft-eid)))

(defn- next-ordinal
  "Compute the next ordinal for a section, defaulting to 0 when empty."
  [conn draft-eid section]
  (let [max-o (dl/q '[:find (max ?o) .
                      :in $ ?draft-eid ?section
                      :where
                      [?d :draft/eid ?draft-eid]
                      [?d :draft/entries ?e]
                      [?e :draft-entry/section ?section]
                      [?e :draft-entry/ordinal ?o]]
                    (dl/db conn) draft-eid section)]
    (if max-o (inc max-o) 0)))

(defn- entry-tx
  "Build the tx-data map for an entry. Skips nil/empty fields so partial
  updates only touch the attrs the caller actually meant to set; the
  required `:eid` is always emitted, while unit/section/ordinal are
  conditional (a partial update typically doesn't repin them)."
  [{:keys [entry-eid unit-eid section ordinal mount lore level
           abilities spells items total-cost engine-cost]}]
  (cond-> {:draft-entry/eid entry-eid}
    unit-eid            (assoc :draft-entry/unit [:unit/eid unit-eid])
    section             (assoc :draft-entry/section section)
    (some? ordinal)     (assoc :draft-entry/ordinal ordinal)
    (some? level)       (assoc :draft-entry/level level)
    mount               (assoc :draft-entry/mount mount)
    lore                (assoc :draft-entry/lore lore)
    (seq abilities)     (assoc :draft-entry/abilities (vec abilities))
    (seq spells)        (assoc :draft-entry/spells (vec spells))
    (seq items)         (assoc :draft-entry/items (vec items))
    (some? total-cost)  (assoc :draft-entry/total-cost (long total-cost))
    (some? engine-cost) (assoc :draft-entry/engine-cost (long engine-cost))))

(defn add-entry!
  "Append an entry to a draft's section. Computes the next ordinal so
  the JSON-array-style ordering is preserved, then transacts the entry
  inline under `:draft/entries`."
  [conn draft-eid {:keys [section] :as entry-spec}]
  (let [ordinal (next-ordinal conn draft-eid section)
        entry   (entry-tx (assoc entry-spec :ordinal ordinal))]
    (dl/transact!
     conn
     [{:draft/eid        draft-eid
       :draft/entries    [entry]
       :draft/updated-at (now-date)
       :draft/version    (inc (or (:draft/version
                                   (dl/pull (dl/db conn) [:draft/version]
                                            (dl/lookup-ref :draft/eid draft-eid)))
                                  1))}])))

(defn remove-entry!
  "Retract an entry by eid. `retractEntity` removes all datoms for the
  entry, which also drops it from the parent's `:draft/entries`
  cardinality-many ref."
  [conn draft-eid entry-eid]
  (dl/transact!
   conn
   [[:db/retractEntity (dl/lookup-ref :draft-entry/eid entry-eid)]
    {:draft/eid        draft-eid
     :draft/updated-at (now-date)
     :draft/version    (inc (or (:draft/version
                                 (dl/pull (dl/db conn) [:draft/version]
                                          (dl/lookup-ref :draft/eid draft-eid)))
                                1))}]))

(defn update-entry!
  "Replace an entry's selections with `new-attrs`. Cardinality-many attrs
  (abilities/spells/items) get a clean retract-all-then-set so the new
  vector is canonical; cardinality-one upserts auto-replace. The entry's
  section and ordinal can be re-pinned to move the entry between sections."
  [conn draft-eid entry-eid new-attrs]
  (let [db       (dl/db conn)
        current  (dl/pull db
                          [:draft-entry/abilities :draft-entry/spells :draft-entry/items
                           :draft-entry/mount :draft-entry/lore]
                          (dl/lookup-ref :draft-entry/eid entry-eid))
        ref      (dl/lookup-ref :draft-entry/eid entry-eid)
        retracts (concat
                  (map (fn [v] [:db/retract ref :draft-entry/abilities v]) (:draft-entry/abilities current))
                  (map (fn [v] [:db/retract ref :draft-entry/spells v])    (:draft-entry/spells current))
                  (map (fn [v] [:db/retract ref :draft-entry/items v])     (:draft-entry/items current))
                  (when (and (:draft-entry/mount current)
                             (nil? (:mount new-attrs)))
                    [[:db/retract ref :draft-entry/mount (:draft-entry/mount current)]])
                  (when (and (:draft-entry/lore current)
                             (nil? (:lore new-attrs)))
                    [[:db/retract ref :draft-entry/lore (:draft-entry/lore current)]]))
        upsert   (entry-tx (merge new-attrs {:entry-eid entry-eid}))]
    (dl/transact!
     conn
     (concat retracts
             [upsert
              {:draft/eid        draft-eid
               :draft/updated-at (now-date)
               :draft/version    (inc (or (:draft/version
                                           (dl/pull db [:draft/version]
                                                    (dl/lookup-ref :draft/eid draft-eid)))
                                          1))}]))))
