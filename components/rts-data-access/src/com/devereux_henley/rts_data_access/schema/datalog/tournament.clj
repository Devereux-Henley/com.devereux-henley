(ns com.devereux-henley.rts-data-access.schema.datalog.tournament
  "Datalevin attributes for the `:tournament` entity — a competition scoped
  to a `:game`, optionally nested under a `:league`/`:season`.

  `:tournament/status` is a keyword enum `#{:registration :active :complete
  :cancelled}`; `:current-phase-index` points at the active phase and
  `:qualifier-count` caps how many entrants advance. Phases hang off the
  cardinality-many `:tournament/phases`. Entries and matches reference the
  tournament via `:tournament-entry/tournament` / `:match/tournament`.
  Standings are computed from entries + completed matches
  (`rules.tournament/recalculate-standings`), so they are not an attribute
  here.")

(def schema
  {:tournament/eid                       {:db/valueType :db.type/uuid
                                          :db/unique    :db.unique/identity}
   :tournament/name                      {:db/valueType :db.type/string}
   :tournament/description               {:db/valueType :db.type/string}
   :tournament/game                      {:db/valueType :db.type/ref}
   :tournament/league                    {:db/valueType :db.type/ref}
   :tournament/season                    {:db/valueType :db.type/ref}
   :tournament/created-by-sub            {:db/valueType :db.type/string}
   :tournament/version                   {:db/valueType :db.type/long}
   :tournament/created-at                {:db/valueType :db.type/instant}
   :tournament/updated-at                {:db/valueType :db.type/instant}
   :tournament/status                    {:db/valueType :db.type/keyword}
   :tournament/registration-opens-at     {:db/valueType :db.type/instant}
   :tournament/registration-closes-at    {:db/valueType :db.type/instant}
   :tournament/timezone                  {:db/valueType :db.type/string}
   :tournament/registration-closed-early {:db/valueType :db.type/boolean}
   :tournament/current-phase-index       {:db/valueType :db.type/long}
   :tournament/qualifier-count           {:db/valueType :db.type/long}
   :tournament/phases                    {:db/valueType   :db.type/ref
                                          :db/cardinality :db.cardinality/many}})
