(ns com.devereux-henley.rts-data-access.schema.datalog
  "Datalevin schema-as-code for the entire database. Applied idempotently when
  the Datalevin connection is opened (see `bases/rts-api/.../datalog.clj`).

  Each domain merges its own attribute map (defined in a sibling namespace
  under `schema.datalog.*`) into [[schema]] here. Conventions:

  - Every domain has a `:<domain>/eid` attribute, `:db.type/uuid` and
    `:db.unique/identity`. Routes, templates, and `match-by-name!` keep working
    unchanged because `[:<domain>/eid uuid]` is the lookup ref everywhere.
  - References use `:db/valueType :db.type/ref`. Set
    `:db/cardinality :db.cardinality/many` when the parent owns a collection.
  - Datalevin is additive at runtime: opening a conn with a superset schema
    only adds the new attributes. Use `datalog.contract/update-schema` at the
    REPL to apply a change without restarting the JVM."
  (:require
   [com.devereux-henley.rts-data-access.schema.datalog.game :as schema.datalog.game]))

(def schema
  "The full Datalevin schema map, assembled from per-domain attribute maps."
  (merge
   schema.datalog.game/schema))
