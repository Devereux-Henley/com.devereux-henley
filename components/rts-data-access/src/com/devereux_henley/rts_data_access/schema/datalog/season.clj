(ns com.devereux-henley.rts-data-access.schema.datalog.season
  "Datalevin attributes for the `:season` entity — a tournament window
  belonging to a `:league`.")

(def schema
  {:season/eid        {:db/valueType :db.type/uuid
                       :db/unique    :db.unique/identity}
   :season/league     {:db/valueType :db.type/ref}
   :season/ordinal    {:db/valueType :db.type/long}
   :season/name       {:db/valueType :db.type/string}
   :season/start-at   {:db/valueType :db.type/instant}
   :season/end-at     {:db/valueType :db.type/instant}
   :season/version    {:db/valueType :db.type/long}
   :season/created-at {:db/valueType :db.type/instant}
   :season/updated-at {:db/valueType :db.type/instant}})
