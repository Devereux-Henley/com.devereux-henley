(ns com.devereux-henley.rts-data-access.schema.datalog.match
  "Datalevin attributes for the `:match` entity — a head-to-head pairing
  within a tournament round. Mirrors the SQLite `match` row.

  The match references its tournament via `:match/tournament`; navigate
  from the tournament with the reverse ref `:match/_tournament`. A
  match's place in the format is kept as the integer pair
  `:match/phase-index` + `:match/round-index` (not refs to
  `:tournament-phase`/`:tournament-round`) because the rules engine groups
  and pairs matches by these indices — `rules.tournament` does
  `(group-by :phase-index …)` and `(get phases phase-index)`, so scalar
  indices are the natural key.

  Enums are promoted to `:db.type/keyword` (membership enforced in the
  domain layer):

  - `:match/status` `#{:pending :complete}`
  - `:match/bracket-type` `#{:winners :losers :grand-final}`

  `:match/format` is the best-of count (1/3/5). `:match/player-one-sub`,
  `…/player-two-sub`, and `…/winner-sub` are Ory subject strings, not refs
  — there is no player entity. `player-two-sub` is absent on a bye;
  `winner-sub` is absent until the match completes (`\"draw\"` is a
  sentinel winner for drawn Swiss/round-robin matches).

  A match owns its games via the cardinality-many `:match/games` ref to
  `:match-game` entities; to find a game's match, pull `:match/_games`.")

(def schema
  {:match/eid            {:db/valueType :db.type/uuid
                          :db/unique    :db.unique/identity}
   :match/tournament     {:db/valueType :db.type/ref}
   :match/phase-index    {:db/valueType :db.type/long}
   :match/round-index    {:db/valueType :db.type/long}
   :match/bracket-type   {:db/valueType :db.type/keyword}
   :match/player-one-sub {:db/valueType :db.type/string}
   :match/player-two-sub {:db/valueType :db.type/string}
   :match/winner-sub     {:db/valueType :db.type/string}
   :match/status         {:db/valueType :db.type/keyword}
   :match/format         {:db/valueType :db.type/long}
   :match/created-at     {:db/valueType :db.type/instant}
   :match/updated-at     {:db/valueType :db.type/instant}
   :match/games          {:db/valueType   :db.type/ref
                          :db/cardinality :db.cardinality/many}})
