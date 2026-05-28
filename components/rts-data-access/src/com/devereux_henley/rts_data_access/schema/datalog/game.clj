(ns com.devereux-henley.rts-data-access.schema.datalog.game
  "Datalevin attributes for the `:game` entity.")

(def schema
  {:game/eid         {:db/valueType :db.type/uuid
                      :db/unique    :db.unique/identity}
   :game/name        {:db/valueType :db.type/string}
   :game/description {:db/valueType :db.type/string}})
