(ns com.devereux-henley.rts-data-access.schema.datalog
  "Datalevin schema-as-code for the entire database. Applied idempotently when
  the Datalevin connection is opened (see `bases/rts-api/.../datalog.clj`).

  Each entity owns its own namespace under `schema.datalog.*`; this namespace
  merges them into a single map. To add a new entity, write a sibling
  namespace exposing `schema` (a map of attribute → spec) and `require` it
  here.

  ## Conventions

  - Every domain entity has a `:<entity>/eid` attribute, `:db.type/uuid` and
    `:db.unique/identity`. Routes, templates, and `match-by-name!` keep
    working unchanged because `[:<entity>/eid uuid]` is the lookup ref
    everywhere.
  - References (SQLite integer FKs) become `:db.type/ref`. Set
    `:db/cardinality :db.cardinality/many` when the parent owns a
    collection.
  - Junction tables stay as their own entities (`:unit-item`,
    `:spell-lore`, `:unit-mount`) rather than collapsing into
    cardinality-many refs. This preserves the existing query surface and
    leaves room for per-membership attributes to land without reshaping
    the pull patterns.
  - Audit columns (`created-by-sub`, `version`, `deleted-at`) from the
    SQLite era are intentionally dropped where the data is regenerated
    from source (e.g. RPFM seeds) rather than mutated. Domains that
    accept user mutations keep them.
  - Datalevin is additive at runtime: opening a conn with a superset
    schema only adds the new attributes. Use
    `datalog.contract/update-schema` at the REPL to apply a change
    without restarting the JVM."
  (:require
   [com.devereux-henley.rts-data-access.schema.datalog.ability :as schema.datalog.ability]
   [com.devereux-henley.rts-data-access.schema.datalog.attribute :as schema.datalog.attribute]
   [com.devereux-henley.rts-data-access.schema.datalog.faction :as schema.datalog.faction]
   [com.devereux-henley.rts-data-access.schema.datalog.game :as schema.datalog.game]
   [com.devereux-henley.rts-data-access.schema.datalog.game-mode :as schema.datalog.game-mode]
   [com.devereux-henley.rts-data-access.schema.datalog.game-social-link :as schema.datalog.game-social-link]
   [com.devereux-henley.rts-data-access.schema.datalog.item :as schema.datalog.item]
   [com.devereux-henley.rts-data-access.schema.datalog.lore :as schema.datalog.lore]
   [com.devereux-henley.rts-data-access.schema.datalog.mount :as schema.datalog.mount]
   [com.devereux-henley.rts-data-access.schema.datalog.patch :as schema.datalog.patch]
   [com.devereux-henley.rts-data-access.schema.datalog.social-media-platform :as schema.datalog.social-media-platform]
   [com.devereux-henley.rts-data-access.schema.datalog.spell :as schema.datalog.spell]
   [com.devereux-henley.rts-data-access.schema.datalog.spell-lore :as schema.datalog.spell-lore]
   [com.devereux-henley.rts-data-access.schema.datalog.subfaction :as schema.datalog.subfaction]
   [com.devereux-henley.rts-data-access.schema.datalog.unit :as schema.datalog.unit]
   [com.devereux-henley.rts-data-access.schema.datalog.unit-category :as schema.datalog.unit-category]
   [com.devereux-henley.rts-data-access.schema.datalog.unit-item :as schema.datalog.unit-item]
   [com.devereux-henley.rts-data-access.schema.datalog.unit-level-cost :as schema.datalog.unit-level-cost]
   [com.devereux-henley.rts-data-access.schema.datalog.unit-mount :as schema.datalog.unit-mount]
   [com.devereux-henley.rts-data-access.schema.datalog.unit-statistics :as schema.datalog.unit-statistics]
   [com.devereux-henley.rts-data-access.schema.datalog.unit-type :as schema.datalog.unit-type]))

(def schema
  "The full Datalevin schema map, assembled from per-entity attribute maps."
  (merge
   schema.datalog.ability/schema
   schema.datalog.attribute/schema
   schema.datalog.faction/schema
   schema.datalog.game/schema
   schema.datalog.game-mode/schema
   schema.datalog.game-social-link/schema
   schema.datalog.item/schema
   schema.datalog.lore/schema
   schema.datalog.mount/schema
   schema.datalog.patch/schema
   schema.datalog.social-media-platform/schema
   schema.datalog.spell/schema
   schema.datalog.spell-lore/schema
   schema.datalog.subfaction/schema
   schema.datalog.unit/schema
   schema.datalog.unit-category/schema
   schema.datalog.unit-item/schema
   schema.datalog.unit-level-cost/schema
   schema.datalog.unit-mount/schema
   schema.datalog.unit-statistics/schema
   schema.datalog.unit-type/schema))
