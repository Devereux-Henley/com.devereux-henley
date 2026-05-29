(ns com.devereux-henley.rts-data-access.schema.datalog.match
  "Datalevin attributes for the `:match` entity — a head-to-head pairing in
  a tournament round. References its tournament via `:match/tournament`;
  its place in the format is the integer pair `:match/phase-index` +
  `:match/round-index`. Enums are keywords: `:match/status` `#{:pending
  :complete}`, `:match/bracket-type` `#{:winners :losers :grand-final}`.
  `:match/format` is the best-of count. Player and winner subs are Ory
  subject strings; `player-two-sub` is absent on a bye and `winner-sub`
  until the match completes (`\"draw\"` marks a drawn match). Owns its
  games via the cardinality-many `:match/games`.")

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
