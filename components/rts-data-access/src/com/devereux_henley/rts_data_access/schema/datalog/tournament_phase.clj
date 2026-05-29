(ns com.devereux-henley.rts-data-access.schema.datalog.tournament-phase
  "Datalevin attributes for the `:tournament-phase` entity — one stage in a
  tournament's format (e.g. a Swiss phase followed by a single-elimination
  cut). Decomposed out of the SQLite `tournament_state.state` JSON blob,
  where phases were a vector under `:phases`.

  `:tournament-phase/ordinal` preserves the JSON-array position, which is
  the integer the rest of the system uses as the phase index
  (`:tournament/current-phase-index`, `:match/phase-index`). Sets in
  datalog are unordered, so an `ordinal` sort restores the prior shape.

  `:tournament-phase/phase-type` is a keyword enum `#{:swiss :round-robin
  :single-elimination :double-elimination}` (membership enforced in the
  domain layer, not by Datalevin).

  A phase owns its rounds via the cardinality-many
  `:tournament-phase/rounds` ref. The blob also stamped runtime fields
  onto each round (`:status`, `:match-eids`); those are intentionally not
  modelled — `rules.tournament/group-matches-by-phase` derives per-round
  state and membership from the `:match` entities themselves (grouped by
  `:match/round-index`), so storing them would only invite drift.")

(def schema
  {:tournament-phase/eid        {:db/valueType :db.type/uuid
                                 :db/unique    :db.unique/identity}
   :tournament-phase/ordinal    {:db/valueType :db.type/long}
   :tournament-phase/phase-type {:db/valueType :db.type/keyword}
   :tournament-phase/rounds     {:db/valueType   :db.type/ref
                                 :db/cardinality :db.cardinality/many}})
