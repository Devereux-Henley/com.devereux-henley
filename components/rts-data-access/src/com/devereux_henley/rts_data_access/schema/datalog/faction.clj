(ns com.devereux-henley.rts-data-access.schema.datalog.faction
  "Datalevin attributes for the `:faction` entity.")

(def schema
  {:faction/eid         {:db/valueType :db.type/uuid
                         :db/unique    :db.unique/identity}
   :faction/name        {:db/valueType :db.type/string}
   :faction/key         {:db/valueType :db.type/string}
   :faction/description {:db/valueType :db.type/string}
   :faction/game        {:db/valueType :db.type/ref}})
