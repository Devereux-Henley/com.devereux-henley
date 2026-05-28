(ns com.devereux-henley.rts-data-access.schema.datalog.unit-type
  "Datalevin attributes for the `:unit-type` entity (e.g. Infantry, Cavalry).")

(def schema
  {:unit-type/eid         {:db/valueType :db.type/uuid
                           :db/unique    :db.unique/identity}
   :unit-type/name        {:db/valueType :db.type/string}
   :unit-type/description {:db/valueType :db.type/string}
   :unit-type/game        {:db/valueType :db.type/ref}})
