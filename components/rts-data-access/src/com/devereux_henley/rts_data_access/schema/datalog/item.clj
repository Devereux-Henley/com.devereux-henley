(ns com.devereux-henley.rts-data-access.schema.datalog.item
  "Datalevin attributes for the `:item` entity (lord/hero equipment items —
  weapons, armour, talismans, banners, enchanted items).")

(def schema
  {:item/eid      {:db/valueType :db.type/uuid
                   :db/unique    :db.unique/identity}
   :item/key      {:db/valueType :db.type/string}
   :item/name     {:db/valueType :db.type/string}
   :item/category {:db/valueType :db.type/string}
   :item/cost     {:db/valueType :db.type/long}
   :item/icon-key {:db/valueType :db.type/string}
   :item/game     {:db/valueType :db.type/ref}})
