(ns com.devereux-henley.rts-data-access.schema.datalog.spell
  "Datalevin attributes for the `:spell` entity. Many-to-many membership in
  `:lore`s lives on `:spell-lore`.")

(def schema
  {:spell/eid         {:db/valueType :db.type/uuid
                       :db/unique    :db.unique/identity}
   :spell/key         {:db/valueType :db.type/string}
   :spell/name        {:db/valueType :db.type/string}
   :spell/description {:db/valueType :db.type/string}
   :spell/spell-type  {:db/valueType :db.type/string}
   :spell/mana-cost   {:db/valueType :db.type/long}
   :spell/cost        {:db/valueType :db.type/long}
   :spell/game        {:db/valueType :db.type/ref}})
