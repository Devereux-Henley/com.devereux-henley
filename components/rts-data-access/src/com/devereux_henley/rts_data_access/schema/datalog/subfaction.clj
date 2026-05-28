(ns com.devereux-henley.rts-data-access.schema.datalog.subfaction
  "Datalevin attributes for the `:subfaction` entity (e.g. Norscan tribes
  within Warriors of Chaos).")

(def schema
  {:subfaction/eid     {:db/valueType :db.type/uuid
                        :db/unique    :db.unique/identity}
   :subfaction/key     {:db/valueType :db.type/string
                        :db/unique    :db.unique/identity}
   :subfaction/name    {:db/valueType :db.type/string}
   :subfaction/faction {:db/valueType :db.type/ref}})
