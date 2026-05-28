(ns com.devereux-henley.rts-data-access.schema.datalog.unit-statistics
  "Datalevin attributes for the `:unit-statistics` entity — a per-patch
  snapshot of a unit's combat statistics.

  The actual statline (health, abilities, every numeric stat, equipment,
  etc.) lives in `:unit-statistics/data`, a Datalevin idoc holding the
  engine-shaped JSON document as-is. We don't query into individual stat
  fields, so storing as a document keeps the schema schema-evolution-
  proof: a new patch that introduces a stat lands without a schema change.

  The blob is read via `(get unit :unit-statistics/data)` and comes back
  as an already-decoded Clojure map (not a JSON string). The SQLite-era
  `parse-unit-statistics` in `rts-domain.handlers.draft` still takes a
  JSON string, so a Datalevin caller must either re-encode the doc with
  `jsonista/write-value-as-string` first or refactor that helper to
  accept a pre-decoded map.

  `:unit-statistics/cost` is denormalized out of the document as a
  first-class `:db.type/long` because the draft / standings queries
  filter and aggregate on it (\"units cheaper than X\", \"sum of unit
  costs for a draft\"). Other fields stay inside the doc; idoc's path
  matching (`datalevin/idoc-match`, `idoc-get`) is available if a future
  query needs them, and a new scalar attribute can always be promoted
  alongside `:cost` without touching the document itself.")

(def schema
  {:unit-statistics/eid   {:db/valueType :db.type/uuid
                           :db/unique    :db.unique/identity}
   ;; Owning ref lives on the many side. Reverse-ref pull
   ;; (`{:unit-statistics/_unit [...]}`) gets every snapshot for a unit;
   ;; `[?s :unit-statistics/unit ?u]` is the query form.
   :unit-statistics/unit  {:db/valueType :db.type/ref}
   :unit-statistics/patch {:db/valueType :db.type/ref}
   :unit-statistics/cost  {:db/valueType :db.type/long}
   :unit-statistics/data  {:db/valueType  :db.type/idoc
                           :db/idocFormat :json}})
