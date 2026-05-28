(ns migrate-draft
  "Offline dev tool: read every active SQLite draft + its JSON `draft_state`
  blob and write the equivalent `:draft` + `:draft-entry` entities into
  Datalevin. One-shot — re-running upserts (all keys are
  `:db.unique/identity`) so this is also the migration recipe for prod.

  Run against the live system after `(claude-workspace/go!)`:

    (require 'migrate-draft :reload)
    (migrate-draft/migrate!)            ; uses the running conn + dev SQLite
    (migrate-draft/verify!)             ; round-trip counts SQLite vs datalog

  Drafts without a `draft_state` row are still migrated (just no
  entries) — the SQLite app creates the row lazily on first edit.

  This namespace is dev-only; it is **not** loaded by the API base."
  (:require
   [com.devereux-henley.datalog.contract :as datalog]
   [com.devereux-henley.rts-api.db :as rts-db]
   [integrant.repl.state :as ig-state]
   [jsonista.core :as jsonista]
   [next.jdbc :as jdbc]
   [next.jdbc.result-set :as rs])
  (:import
   [java.time Instant]
   [java.util Date UUID]))

;;; ─── Helpers ──────────────────────────────────────────────────────────────

(def ^:private state-object-mapper
  (jsonista/object-mapper {:decode-key-fn keyword}))

(def ^:private jdbc-opts
  {:builder-fn rs/as-unqualified-kebab-maps})

(defn- ->uuid
  [s]
  (when s (UUID/fromString s)))

(defn- ->long
  "Coerce a value to long. Legacy state entries from before schema
  enforcement stored some ints as strings (`\"500\"`, `\"0\"`); this
  normalises them. Returns nil for nil input."
  [v]
  (cond
    (nil? v)     nil
    (integer? v) (long v)
    (string? v)  (Long/parseLong v)
    :else        (long v)))

(defn- parse-instant
  [s]
  (when s (Date/from (Instant/parse s))))

(defn- assoc-some
  "Like `assoc`, but skips keys whose value is nil or an empty string."
  [m k v]
  (if (or (nil? v) (= "" v))
    m
    (assoc m k v)))

;;; ─── Entry → tx-data ─────────────────────────────────────────────────────

(defn- ->entry-tx
  "Turn one decoded JSON state-entry into a `:draft-entry/*` tx map.
  `section` is `:main` or `:reinforcements`; `ordinal` preserves the
  source array position."
  [section ordinal {:keys [entry-eid unit-eid mount lore level
                           abilities spells items
                           total-cost engine-cost]}]
  (cond-> {:draft-entry/eid     (or (->uuid entry-eid) (random-uuid))
           :draft-entry/unit    [:unit/eid (->uuid unit-eid)]
           :draft-entry/section section
           :draft-entry/ordinal ordinal
           :draft-entry/level   (or (->long level) 0)}
    mount               (assoc :draft-entry/mount mount)
    lore                (assoc :draft-entry/lore lore)
    (seq abilities)     (assoc :draft-entry/abilities (vec abilities))
    (seq spells)        (assoc :draft-entry/spells (vec spells))
    (seq items)         (assoc :draft-entry/items (vec items))
    (some? total-cost)  (assoc :draft-entry/total-cost (->long total-cost))
    (some? engine-cost) (assoc :draft-entry/engine-cost (->long engine-cost))))

(defn- ->draft-tx
  "Turn one SQLite draft row (joined with its optional state blob and the
  parent game-mode / faction eids) into a `:draft/*` tx map carrying its
  entries inline under `:draft/entries`."
  [{:keys [eid name player-sub version created-by-sub created-at updated-at
           faction-eid game-mode-eid state]}]
  (let [parsed (when state (jsonista/read-value state state-object-mapper))
        main   (->> (:main parsed)
                    (map-indexed (fn [i e] (->entry-tx :main i e))))
        reinf  (->> (:reinforcements parsed)
                    (map-indexed (fn [i e] (->entry-tx :reinforcements i e))))]
    (cond-> {:draft/eid            (->uuid eid)
             :draft/player-sub     player-sub
             :draft/game-mode      [:game-mode/eid (->uuid game-mode-eid)]
             :draft/faction        [:faction/eid (->uuid faction-eid)]
             :draft/version        version
             :draft/created-by-sub created-by-sub
             :draft/created-at     (parse-instant created-at)
             :draft/updated-at     (parse-instant updated-at)
             :draft/entries        (vec (concat main reinf))}
      name (assoc-some :draft/name name))))

;;; ─── Orchestrator ─────────────────────────────────────────────────────────

(def ^:private read-drafts-sql
  "SELECT d.eid, d.name, d.player_sub, d.version,
          d.created_by_sub, d.created_at, d.updated_at,
          f.eid  AS faction_eid,
          gm.eid AS game_mode_eid,
          ds.state
     FROM draft d
     JOIN faction   f  ON f.id  = d.faction_id
     JOIN game_mode gm ON gm.id = d.game_mode_id
     LEFT JOIN draft_state ds ON ds.draft_id = d.id
    WHERE d.deleted_at IS NULL
    ORDER BY d.id")

(defn- read-rows
  [db-spec]
  (jdbc/execute! db-spec [read-drafts-sql] jdbc-opts))

(defn- conn-or-throw
  []
  (or (get ig-state/system :com.devereux-henley.rts-api.datalog/connection)
      (throw (ex-info "Datalevin conn not in integrant.repl.state/system — call (claude-workspace/go!) first" {}))))

(defn migrate!
  "Migrate every active SQLite draft into Datalevin. Returns a summary
  map `{:drafts n :entries n}`. Idempotent — re-runs upsert via the
  `:db.unique/identity` on `:draft/eid` and `:draft-entry/eid`."
  ([] (migrate! {}))
  ([{:keys [db-spec conn]
     :or   {db-spec rts-db/db-spec
            conn    (conn-or-throw)}}]
   (let [rows        (read-rows db-spec)
         txs         (mapv ->draft-tx rows)
         entry-count (transduce (map #(count (:draft/entries %))) + 0 txs)]
     (println (format "Migrating %d draft(s), %d entry(ies) …"
                      (count txs) entry-count))
     (datalog/transact! conn txs)
     (println "Done.")
     {:drafts (count txs) :entries entry-count})))

(defn verify!
  "Cross-check counts after a migration. Compares the SQLite draft row
  count against the Datalevin `[?d :draft/eid]` count, and the sum of
  JSON entries against the `[?e :draft-entry/eid]` count."
  ([] (verify! {}))
  ([{:keys [db-spec conn]
     :or   {db-spec rts-db/db-spec
            conn    (conn-or-throw)}}]
   (let [sqlite-drafts  (-> (jdbc/execute-one!
                             db-spec
                             ["SELECT COUNT(*) AS c FROM draft WHERE deleted_at IS NULL"]
                             jdbc-opts)
                            :c)
         sqlite-entries (transduce
                         (map (fn [{:keys [state]}]
                                (if state
                                  (let [p (jsonista/read-value state state-object-mapper)]
                                    (+ (count (:main p)) (count (:reinforcements p))))
                                  0)))
                         +
                         0
                         (read-rows db-spec))
         db             (datalog/db conn)
         dl-drafts      (or (datalog/q '[:find (count ?d) . :where [?d :draft/eid]] db) 0)
         dl-entries     (or (datalog/q '[:find (count ?e) . :where [?e :draft-entry/eid]] db) 0)]
     (println (format "SQLite:   %4d drafts, %4d entries" sqlite-drafts sqlite-entries))
     (println (format "Datalog:  %4d drafts, %4d entries" dl-drafts dl-entries))
     {:sqlite/drafts   sqlite-drafts
      :sqlite/entries  sqlite-entries
      :datalog/drafts  dl-drafts
      :datalog/entries dl-entries
      :match?          (and (= sqlite-drafts dl-drafts)
                            (= sqlite-entries dl-entries))})))

(comment
  ;; After `(claude-workspace/go!)` + `(claude-workspace/seed-datalog!)`:
  (migrate!)
  (verify!))
