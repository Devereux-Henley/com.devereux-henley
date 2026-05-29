(ns com.devereux-henley.rts-data-access.schema.datalog.league
  "Datalevin attributes for the `:league` entity — a player-organized
  container for tournament seasons, scoped to a single `:game`.")

(def schema
  {:league/eid            {:db/valueType :db.type/uuid
                           :db/unique    :db.unique/identity}
   :league/game           {:db/valueType :db.type/ref}
   :league/name           {:db/valueType :db.type/string}
   :league/description    {:db/valueType :db.type/string}
   :league/created-by-sub {:db/valueType :db.type/string}
   :league/version        {:db/valueType :db.type/long}
   :league/created-at     {:db/valueType :db.type/instant}
   :league/updated-at     {:db/valueType :db.type/instant}})
