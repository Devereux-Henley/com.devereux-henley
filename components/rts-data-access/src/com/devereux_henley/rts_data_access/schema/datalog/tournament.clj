(ns com.devereux-henley.rts-data-access.schema.datalog.tournament
  "Datalevin attributes for the `:tournament` entity — a player-organized
  competition scoped to a single `:game`, optionally nested under a
  `:league`/`:season`.

  Tournaments are user-mutated, so the SQLite-era audit columns
  (`:tournament/created-by-sub`, `:tournament/version`,
  `:tournament/created-at`, `:tournament/updated-at`) survive the
  migration. Soft-delete (`deleted_at`) is dropped — datalog retracts the
  entity instead.

  ## State blob decomposition

  The SQLite `tournament_state.state` JSON blob is folded onto this entity
  and its owned children rather than persisted as an opaque document:

  - `:status` → `:tournament/status` (keyword enum, see below)
  - `:registration` → `:tournament/registration-opens-at`,
    `…/registration-closes-at`, `…/timezone`,
    `…/registration-closed-early`
  - `:current-phase` → `:tournament/current-phase-index`
  - `:qualifier-count` → `:tournament/qualifier-count`
  - `:phases` → the cardinality-many `:tournament/phases` ref to
    `:tournament-phase` entities (which in turn own `:tournament-round`s)
  - `:standings` → NOT stored. Standings are derived at the query layer
    from `:tournament-entry` participants + completed `:match` results
    (see `rules.tournament/recalculate-standings`), so there is no
    snapshot to drift.

  `:tournament/status` is a keyword enum `#{:registration :active
  :complete :cancelled}` (mirrors `:unit/mark`'s SQLite-CHECK-becomes-
  domain-invariant pattern; the membership set is enforced in the domain
  layer, not by Datalevin).

  Entries and matches are NOT owned forward — they reference the
  tournament (`:tournament-entry/tournament`, `:match/tournament`) and are
  navigated via the reverse refs `:tournament-entry/_tournament` /
  `:match/_tournament`. They are created, queried, and retracted
  independently, so reverse ownership keeps those operations from having
  to read-modify-write a parent collection. Phases, by contrast, are
  configuration replaced wholesale, so they hang off the forward
  cardinality-many `:tournament/phases`.")

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
