(ns com.devereux-henley.rts-data-access.schema.datalog.item
  "Datalevin attributes for the `:item` entity (lord/hero equipment items —
  weapons, armour, talismans, banners, enchanted items).")

(def schema
  {:item/eid                 {:db/valueType :db.type/uuid
                              :db/unique    :db.unique/identity}
   :item/key                 {:db/valueType :db.type/string}
   :item/name                {:db/valueType :db.type/string}
   :item/category            {:db/valueType :db.type/string}
   :item/cost                {:db/valueType :db.type/long}
   :item/icon-key            {:db/valueType :db.type/string}
   ;; Engine ability keys this item grants, as they surface in a parsed
   ;; replay's UNIT_ABILITIES (`_item_passive_…` / `_item_ability_…`). Joined
   ;; against a replay unit's equipped-ability keys to recover the item.
   :item/replay-ability-keys {:db/valueType   :db.type/string
                              :db/cardinality :db.cardinality/many}
   :item/game                {:db/valueType :db.type/ref}})
