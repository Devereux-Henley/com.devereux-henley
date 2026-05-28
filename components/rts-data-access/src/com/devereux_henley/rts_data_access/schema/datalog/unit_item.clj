(ns com.devereux-henley.rts-data-access.schema.datalog.unit-item
  "Datalevin attributes for the `:unit-item` junction entity. Kept as its own
  entity (rather than collapsing into a cardinality-many ref) so a future
  per-membership attribute (e.g. a `:default?` flag or a quantity) can land
  without reshaping the pull patterns.")

(def schema
  {:unit-item/eid  {:db/valueType :db.type/uuid
                    :db/unique    :db.unique/identity}
   :unit-item/unit {:db/valueType :db.type/ref}
   :unit-item/item {:db/valueType :db.type/ref}})
