(ns com.devereux-henley.rts-data-access.query.datalog.draft
  "Datalevin reads and mutations for the draft domain."
  (:require
   [clojure.set :as set]
   [com.devereux-henley.datalog.contract :as dl]
   [com.devereux-henley.schema.contract :as schema.contract]
   [malli.core :as m])
  (:import
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

;;; ─── Return schemas ───────────────────────────────────────────────────────

(def draft-entry-schema
  "Flat-state shape returned by `draft-state-by-eid` and
  `draft-entry-by-eid`."
  (schema.contract/to-schema
   [:map
    [:entry-eid   :uuid]
    [:unit-eid    :uuid]
    [:section     [:enum :main :reinforcements]]
    [:ordinal     [:int {:min 0}]]
    [:level       [:int {:min 0}]]
    [:mount       {:optional true} :string]
    [:lore        {:optional true} :string]
    [:abilities   {:optional true} [:sequential :string]]
    [:spells      {:optional true} [:sequential :string]]
    [:items       {:optional true} [:sequential :string]]
    [:total-cost  {:optional true} :int]
    [:engine-cost {:optional true} :int]]))

(def draft-state-schema
  "Sectioned bag of entries returned by `draft-state-by-eid`."
  (schema.contract/to-schema
   [:map
    [:main           [:sequential draft-entry-schema]]
    [:reinforcements [:sequential draft-entry-schema]]]))

(def draft-result-schema
  "Flat draft row returned by `draft-by-eid` + the listing queries."
  (schema.contract/to-schema
   [:map
    [:eid            :uuid]
    [:name           [:maybe :string]]
    [:player-sub     :string]
    [:version       :int]
    [:game-mode-eid [:maybe :uuid]]
    [:faction-eid   [:maybe :uuid]]
    [:faction-name  [:maybe :string]]
    [:game-eid      [:maybe :uuid]]
    [:created-at    [:maybe inst?]]
    [:updated-at    [:maybe inst?]]
    [:created-by-sub {:optional true} :string]]))

(def create-spec-schema
  (schema.contract/to-schema
   [:map
    [:eid            :uuid]
    [:player-sub     :string]
    [:game-mode-eid  :uuid]
    [:faction-eid    :uuid]
    [:name           {:optional true} [:maybe :string]]
    [:created-by-sub {:optional true} [:maybe :string]]]))

(def entry-add-spec-schema
  (schema.contract/to-schema
   [:map
    [:entry-eid   :uuid]
    [:unit-eid    :uuid]
    [:section     [:enum :main :reinforcements]]
    [:level       {:optional true} :int]
    [:mount       {:optional true} [:maybe :string]]
    [:lore        {:optional true} [:maybe :string]]
    [:abilities   {:optional true} [:sequential :string]]
    [:spells      {:optional true} [:sequential :string]]
    [:items       {:optional true} [:sequential :string]]
    [:total-cost  {:optional true} [:maybe :int]]
    [:engine-cost {:optional true} [:maybe :int]]]))

(def entry-update-attrs-schema
  (schema.contract/to-schema
   [:map
    [:unit-eid    {:optional true} :uuid]
    [:section     {:optional true} [:enum :main :reinforcements]]
    [:level       {:optional true} :int]
    [:mount       {:optional true} [:maybe :string]]
    [:lore        {:optional true} [:maybe :string]]
    [:abilities   {:optional true} [:sequential :string]]
    [:spells      {:optional true} [:sequential :string]]
    [:items       {:optional true} [:sequential :string]]
    [:total-cost  {:optional true} [:maybe :int]]
    [:engine-cost {:optional true} [:maybe :int]]]))

(def conn-schema
  "Opaque Datalevin connection — pre-validating it costs more than the
  fns it'd be wrapping and would couple this ns to datalevin internals."
  :any)

(def tx-report-schema
  "Datalevin transact!'s return is the full tx report; queries pass it
  through unchanged."
  :any)

;;; ─── Result builders ──────────────────────────────────────────────────────

(defn- ->draft
  "Flatten a draft pull result into the unqualified-key shape the
  SQLite-era handlers expect: ref sub-maps become flat `*-eid` fields.
  Dates stay as canonical `java.util.Date` values — formatting belongs
  to the web layer (Selmer `:date` filter on the template)."
  [m]
  (when m
    (let [faction   (:draft/faction m)
          game-mode (:draft/game-mode m)]
      (cond-> {:eid           (:draft/eid m)
               :name          (:draft/name m)
               :player-sub    (:draft/player-sub m)
               :version       (:draft/version m)
               :game-mode-eid (some-> game-mode :game-mode/eid)
               :faction-eid   (some-> faction :faction/eid)
               :faction-name  (some-> faction :faction/name)
               :game-eid      (some-> game-mode :game-mode/game :game/eid)
               :created-at    (:draft/created-at m)
               :updated-at    (:draft/updated-at m)}
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
  "Fetch a draft by eid. Returns nil when not found."
  [conn eid]
  (->draft (dl/pull (dl/db conn) draft-pattern (dl/lookup-ref :draft/eid eid))))

(m/=> draft-by-eid
      [:=> [:cat conn-schema :uuid] [:maybe draft-result-schema]])

(defn drafts-for-player
  "All drafts a player owns, sorted updated-at desc (eid tiebreak)."
  [conn player-sub]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?d pattern) ...]
                 :in $ pattern ?player-sub
                 :where [?d :draft/player-sub ?player-sub]]
               db draft-pattern player-sub)
         (mapv ->draft)
         (sort-by (juxt (comp - (fnil #(.getTime ^Date %) (Date. 0)) :updated-at) :eid))
         vec)))

(m/=> drafts-for-player
      [:=> [:cat conn-schema :string] [:sequential draft-result-schema]])

(defn drafts-for-player-by-game
  "Drafts a player owns, scoped through the game-mode's parent game ref.
  Sorted updated-at desc."
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

(m/=> drafts-for-player-by-game
      [:=> [:cat conn-schema :string :uuid] [:sequential draft-result-schema]])

(defn draft-state-by-eid
  "Sectioned army state for a draft."
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

(m/=> draft-state-by-eid
      [:=> [:cat conn-schema :uuid] draft-state-schema])

(defn draft-entry-by-eid
  "Fetch a single entry by eid, or nil when not found."
  [conn entry-eid]
  (->entry (dl/pull (dl/db conn) entry-pattern (dl/lookup-ref :draft-entry/eid entry-eid))))

(m/=> draft-entry-by-eid
      [:=> [:cat conn-schema :uuid] [:maybe draft-entry-schema]])

(defn draft-entry-section-and-ordinal
  "Where an entry sits — its section + ordinal. Avoids re-reading the
  full draft state when validation only needs the placement."
  [conn entry-eid]
  (let [m (dl/pull (dl/db conn) [:draft-entry/section :draft-entry/ordinal]
                   (dl/lookup-ref :draft-entry/eid entry-eid))]
    (when (:draft-entry/section m)
      {:section (:draft-entry/section m)
       :ordinal (:draft-entry/ordinal m)})))

(m/=> draft-entry-section-and-ordinal
      [:=> [:cat conn-schema :uuid]
       [:maybe [:map
                [:section [:enum :main :reinforcements]]
                [:ordinal :int]]]])

;;; ─── Tx-builders + transact entry points ──────────────────────────────────

(defn- now-date [] (Date.))

(defn create-draft!
  "Transact a new draft. `:created-by-sub` defaults to `:player-sub`
  when the caller doesn't differentiate."
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

(m/=> create-draft!
      [:=> [:cat conn-schema create-spec-schema] draft-result-schema])

(defn update-draft-name!
  "Update a draft's mutable name, bumping version + updated-at. Passing
  `nil` retracts the stored name so the faction+date default renders
  again."
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

(m/=> update-draft-name!
      [:=> [:cat conn-schema :uuid [:maybe :string]] draft-result-schema])

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
  "Append an entry to a draft's section. The next ordinal is computed
  from the current state so insertion-order survives."
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

(m/=> add-entry!
      [:=> [:cat conn-schema :uuid entry-add-spec-schema] tx-report-schema])

(defn remove-entry!
  "Retract an entry by eid. The cardinality-many link from the parent's
  `:draft/entries` drops out as a side effect of `:db/retractEntity`."
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

(m/=> remove-entry!
      [:=> [:cat conn-schema :uuid :uuid] tx-report-schema])

(defn- cardinality-many-diff-ops
  "Compute the minimal retract/add tx-data to transition a
  cardinality-many attribute from the current value set to the desired
  one. Only the symmetric difference is emitted — values present in
  both stay put, so a typical edit that only changes a mount issues
  zero ops for abilities/spells/items."
  [ref attr current desired]
  (let [current-set (set current)
        desired-set (set desired)
        to-retract  (set/difference current-set desired-set)
        to-add      (set/difference desired-set current-set)]
    (concat (map (fn [v] [:db/retract ref attr v]) to-retract)
            (map (fn [v] [:db/add ref attr v])     to-add))))

(defn update-entry!
  "Replace an entry's selections. Cardinality-many attrs
  (abilities/spells/items) are reconciled with a symmetric-difference
  pass; cardinality-one upserts auto-replace; `:section`/`:ordinal`
  can be re-pinned to move the entry between sections."
  [conn draft-eid entry-eid new-attrs]
  (let [db          (dl/db conn)
        current     (dl/pull db
                             [:draft-entry/abilities :draft-entry/spells :draft-entry/items
                              :draft-entry/mount :draft-entry/lore]
                             (dl/lookup-ref :draft-entry/eid entry-eid))
        ref         (dl/lookup-ref :draft-entry/eid entry-eid)
        cm-ops      (concat
                     (cardinality-many-diff-ops ref :draft-entry/abilities
                                                (:draft-entry/abilities current)
                                                (:abilities new-attrs))
                     (cardinality-many-diff-ops ref :draft-entry/spells
                                                (:draft-entry/spells current)
                                                (:spells new-attrs))
                     (cardinality-many-diff-ops ref :draft-entry/items
                                                (:draft-entry/items current)
                                                (:items new-attrs)))
        co-retracts (concat
                     (when (and (:draft-entry/mount current)
                                (nil? (:mount new-attrs)))
                       [[:db/retract ref :draft-entry/mount (:draft-entry/mount current)]])
                     (when (and (:draft-entry/lore current)
                                (nil? (:lore new-attrs)))
                       [[:db/retract ref :draft-entry/lore (:draft-entry/lore current)]]))
        upsert      (entry-tx (merge (dissoc new-attrs :abilities :spells :items)
                                     {:entry-eid entry-eid}))]
    (dl/transact!
     conn
     (concat cm-ops co-retracts
             [upsert
              {:draft/eid        draft-eid
               :draft/updated-at (now-date)
               :draft/version    (inc (or (:draft/version
                                           (dl/pull db [:draft/version]
                                                    (dl/lookup-ref :draft/eid draft-eid)))
                                          1))}]))))

(m/=> update-entry!
      [:=> [:cat conn-schema :uuid :uuid entry-update-attrs-schema] tx-report-schema])
