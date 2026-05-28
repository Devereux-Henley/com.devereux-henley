(ns com.devereux-henley.rts-data-access.schema.datalog.lore
  "Datalevin attributes for the `:lore` entity (Lores of Magic, e.g. Lore of
  Fire, Lore of Beasts).")

(def schema
  {:lore/eid         {:db/valueType :db.type/uuid
                      :db/unique    :db.unique/identity}
   :lore/key         {:db/valueType :db.type/string}
   :lore/name        {:db/valueType :db.type/string}
   :lore/description {:db/valueType :db.type/string}
   :lore/game        {:db/valueType :db.type/ref}})
