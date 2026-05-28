(ns com.devereux-henley.rts-data-access.schema.datalog
  "Datalevin schema-as-code for the entire database. Applied idempotently when
  the Datalevin connection is opened (see `bases/rts-api/.../datalog.clj`).

  Each domain epic merges its own attribute map into [[schema]] as it migrates
  off SQLite. Conventions:

  - Every domain has a `:<domain>/eid` attribute, `:db.type/uuid` and
    `:db.unique/identity`. Routes, templates, and `match-by-name!` keep working
    unchanged because `[:<domain>/eid uuid]` is the lookup ref everywhere.
  - References use `:db/valueType :db.type/ref`. Set
    `:db/cardinality :db.cardinality/many` when the parent owns a collection.
  - Datalevin is additive at runtime: opening a conn with a superset schema
    only adds the new attributes. Use `datalog.contract/update-schema` at the
    REPL to apply a change without restarting the JVM.")

(def schema
  "The full Datalevin schema map. Per-domain attribute maps merge in here as
  each domain migrates off SQLite. Empty until the game-domain pilot lands."
  {})
