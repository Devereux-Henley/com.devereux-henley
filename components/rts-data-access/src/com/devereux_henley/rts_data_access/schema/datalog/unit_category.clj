(ns com.devereux-henley.rts-data-access.schema.datalog.unit-category
  "Datalevin attributes for the `:unit-category` entity (e.g. Core, Special,
  Rare, Lord, Hero).")

(def schema
  {:unit-category/eid         {:db/valueType :db.type/uuid
                               :db/unique    :db.unique/identity}
   :unit-category/name        {:db/valueType :db.type/string}
   :unit-category/description {:db/valueType :db.type/string}
   :unit-category/ordinal     {:db/valueType :db.type/long}
   :unit-category/game        {:db/valueType :db.type/ref}})
