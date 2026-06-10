(ns com.devereux-henley.rts-data-access.schema.datalog.item
  "Datalevin attributes for the `:item` entity (lord/hero equipment items —
  weapons, armour, talismans, banners, enchanted items).")

(def schema
  {:item/eid       {:db/valueType :db.type/uuid
                    :db/unique    :db.unique/identity}
   :item/key       {:db/valueType :db.type/string}
   :item/name      {:db/valueType :db.type/string}
   :item/category  {:db/valueType :db.type/string}
   :item/cost      {:db/valueType :db.type/long}
   :item/icon-key  {:db/valueType :db.type/string}
   ;; The `:ability` rows this item grants — engine keys as they surface in a
   ;; parsed replay's UNIT_ABILITIES (`_item_passive_…` / `_item_ability_…`).
   ;; Joining a replay unit's equipped-ability keys through here recovers the
   ;; item; an ability granted by several items is referenced by each.
   :item/abilities {:db/valueType   :db.type/ref
                    :db/cardinality :db.cardinality/many}
   :item/game      {:db/valueType :db.type/ref}})
