(ns com.devereux-henley.rts-data-access.schema.datalog.season
  "Datalevin attributes for the `:season` entity — a tournament window
  belonging to a `:league`.

  `:season/ordinal` orders seasons within a league. The SQLite era
  enforced `UNIQUE(league_id, ordinal)`; here that invariant is
  maintained at the handler layer by computing the next ordinal as
  `MAX(ordinal)+1` over the league's existing seasons before
  transacting.

  `:season/name` is optional. The domain handler falls back to
  `\"Season N\"` (where N is `:season/ordinal`) when rendering.

  `:season/start-at` and `:season/end-at` are stored as instants; the
  caller converts wall-clock + timezone to UTC before transacting.
  The active-season query takes the max ordinal among seasons whose
  `:end-at` is in the future:

    (datalog/q '[:find (pull ?s […])
                 :in $ ?league-eid ?now
                 :where
                 [?l :league/eid ?league-eid]
                 [?s :season/league ?l]
                 [?s :season/end-at ?end]
                 [(<= ?now ?end)]]
               db league-eid (java.util.Date.))

  Audit columns (`:season/version`, `:season/created-at`,
  `:season/updated-at`) survive the migration — seasons are user
  mutated. `:season/created-by-sub` is dropped because the ownership
  check rides on the parent league.")

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
