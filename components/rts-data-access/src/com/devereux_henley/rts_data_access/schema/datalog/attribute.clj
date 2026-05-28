(ns com.devereux-henley.rts-data-access.schema.datalog.attribute
  "Datalevin attributes for the `:attribute` entity (passive unit attributes
  like Causes Fear, Immune to Psychology, Spear Wall).")

(def schema
  {:attribute/eid         {:db/valueType :db.type/uuid
                           :db/unique    :db.unique/identity}
   :attribute/key         {:db/valueType :db.type/string}
   :attribute/name        {:db/valueType :db.type/string}
   :attribute/description {:db/valueType :db.type/string}
   :attribute/game        {:db/valueType :db.type/ref}})
