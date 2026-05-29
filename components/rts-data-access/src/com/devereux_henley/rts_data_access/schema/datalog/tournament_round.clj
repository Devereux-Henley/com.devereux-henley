(ns com.devereux-henley.rts-data-access.schema.datalog.tournament-round
  "Datalevin attributes for the `:tournament-round` entity — one round of
  play within a `:tournament-phase`. Decomposed out of the SQLite
  `tournament_state.state` JSON blob, where rounds were a vector under each
  phase's `:rounds`.

  `:tournament-round/round-index` is the round's position within its phase
  (0-based); it doubles as the ordinal, since rounds are always referenced
  by this index (`:match/round-index`, qualifier cuts). `…/format` is the
  best-of count (1/3/5) every match generated for the round inherits.

  Rounds carry no `:status` or match list — whether a round has been
  generated and which matches belong to it is derived from the `:match`
  entities (those with a matching `:match/phase-index` +
  `:match/round-index`), so the round entity stays pure configuration.

  Rounds are owned by their phase via the cardinality-many
  `:tournament-phase/rounds`; to find a round's phase, pull
  `:tournament-phase/_rounds`.")

(def schema
  {:tournament-round/eid         {:db/valueType :db.type/uuid
                                  :db/unique    :db.unique/identity}
   :tournament-round/round-index {:db/valueType :db.type/long}
   :tournament-round/format      {:db/valueType :db.type/long}})
