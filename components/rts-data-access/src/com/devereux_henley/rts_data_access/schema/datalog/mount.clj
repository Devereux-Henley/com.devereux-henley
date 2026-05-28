(ns com.devereux-henley.rts-data-access.schema.datalog.mount
  "Datalevin attributes for the `:mount` entity. The per-unit cost / stat
  overrides live on `:unit-mount`, not here.")

(def schema
  {:mount/eid      {:db/valueType :db.type/uuid
                    :db/unique    :db.unique/identity}
   :mount/key      {:db/valueType :db.type/string}
   :mount/name     {:db/valueType :db.type/string}
   :mount/icon-key {:db/valueType :db.type/string}
   :mount/game     {:db/valueType :db.type/ref}})
