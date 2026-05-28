(ns com.devereux-henley.rts-data-access.schema.datalog.spell-lore
  "Datalevin attributes for the `:spell-lore` junction entity. Kept as its own
  entity (rather than collapsing into a cardinality-many ref) so per-membership
  attributes can land later without reshaping the pull patterns.")

(def schema
  {:spell-lore/eid   {:db/valueType :db.type/uuid
                      :db/unique    :db.unique/identity}
   :spell-lore/spell {:db/valueType :db.type/ref}
   :spell-lore/lore  {:db/valueType :db.type/ref}
   :spell-lore/game  {:db/valueType :db.type/ref}})
