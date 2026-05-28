(ns com.devereux-henley.rts-data-access.schema.datalog.unit-statistics
  "Datalevin attributes for the `:unit-statistics` entity — a per-patch
  snapshot of a unit's combat statistics. Replaces the opaque JSON blob the
  SQLite era stored in `unit.unit_statistics`.

  Dynamic per-engine numeric stats (`armor`, `leadership`, `speed`, …) hang
  off `:unit-statistics/stats` as `:unit-stat` sub-entities so the set of
  stats can grow per-patch without a schema migration. Each sub-entity
  carries a deterministic `:unit-stat/eid` derived from
  `(unit-statistics-eid, stat-key)` so re-running the seed loader upserts
  in place instead of accumulating duplicate stats under the parent.

  Equipment retains the engine's \"vector of arbitrary maps\" shape via an
  EDN-serialized string until a domain epic needs to query into it;
  flattening it now would be speculative.")

(def schema
  {;; Snapshot
   :unit-statistics/eid                    {:db/valueType :db.type/uuid
                                            :db/unique    :db.unique/identity}
   :unit-statistics/patch                  {:db/valueType :db.type/ref}
   :unit-statistics/health                 {:db/valueType :db.type/long}
   :unit-statistics/barrier                {:db/valueType :db.type/long}
   :unit-statistics/abilities              {:db/valueType   :db.type/string
                                            :db/cardinality :db.cardinality/many}
   :unit-statistics/draftable-spell-keys   {:db/valueType   :db.type/string
                                            :db/cardinality :db.cardinality/many}
   :unit-statistics/draftable-ability-keys {:db/valueType   :db.type/string
                                            :db/cardinality :db.cardinality/many}
   :unit-statistics/attributes             {:db/valueType   :db.type/string
                                            :db/cardinality :db.cardinality/many}
   :unit-statistics/stats                  {:db/valueType   :db.type/ref
                                            :db/cardinality :db.cardinality/many}
   :unit-statistics/equipment              {:db/valueType :db.type/string}

   ;; Sub-entity: one per (unit-statistics, stat name) pair. Engine emits
   ;; some stats as numbers (`"armor": 80`) and others as strings
   ;; (`"ammunition": "30"`), so the value is stored as a string. `eid` is
   ;; deterministic (UUIDv3 of `unit-statistics-eid + "/" + key`) so the
   ;; seed loader can upsert without leaking stale sub-entities.
   :unit-stat/eid                          {:db/valueType :db.type/uuid
                                            :db/unique    :db.unique/identity}
   :unit-stat/key                          {:db/valueType :db.type/string}
   :unit-stat/value                        {:db/valueType :db.type/string}})
