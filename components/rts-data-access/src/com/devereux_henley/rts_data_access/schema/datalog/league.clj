(ns com.devereux-henley.rts-data-access.schema.datalog.league
  "Datalevin attributes for the `:league` entity — a player-organized
  container for tournament seasons, scoped to a single `:game`.

  Leagues are user-mutated, so the SQLite-era audit columns
  (`:league/created-by-sub`, `:league/version`, `:league/created-at`,
  `:league/updated-at`) survive the migration.
  `:league/created-by-sub` drives the ownership check that gates
  season creation under a league.

  The SQLite `deleted_at` soft-delete column is dropped — Datalevin
  retract semantics replace it.

  Seasons hang off the league via `:season/league` (a ref on the
  season side). To list a league's seasons:

    (datalog/q '[:find (pull ?s […])
                 :in $ ?league-eid
                 :where
                 [?l :league/eid ?league-eid]
                 [?s :season/league ?l]]
               db league-eid)")

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
