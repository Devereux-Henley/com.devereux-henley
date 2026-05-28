(ns com.devereux-henley.rts-data-access.schema.datalog.unit-mount
  "Datalevin attributes for the `:unit-mount` junction entity. Unlike
  `:unit-item` / `:spell-lore`, this junction carries real per-membership
  data — cost, statline override, and a set of granted ability keys — so it
  must stay an entity rather than collapse into a cardinality-many ref.")

(def schema
  {:unit-mount/eid                  {:db/valueType :db.type/uuid
                                     :db/unique    :db.unique/identity}
   :unit-mount/unit                 {:db/valueType :db.type/ref}
   :unit-mount/mount                {:db/valueType :db.type/ref}
   :unit-mount/cost                 {:db/valueType :db.type/long}
   :unit-mount/stats-override       {:db/valueType :db.type/string}
   :unit-mount/granted-ability-keys {:db/valueType   :db.type/string
                                     :db/cardinality :db.cardinality/many}})
