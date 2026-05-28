(ns com.devereux-henley.rts-data-access.schema.datalog.ability
  "Datalevin attributes for the `:ability` entity (unit-granted abilities like
  Charge, Brace, Vanguard Deployment).")

(def schema
  {:ability/eid          {:db/valueType :db.type/uuid
                          :db/unique    :db.unique/identity}
   :ability/key          {:db/valueType :db.type/string}
   :ability/name         {:db/valueType :db.type/string}
   :ability/description  {:db/valueType :db.type/string}
   :ability/ability-type {:db/valueType :db.type/string}
   :ability/cost         {:db/valueType :db.type/long}
   :ability/game         {:db/valueType :db.type/ref}})
