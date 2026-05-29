(ns com.devereux-henley.rts-data-access.schema.datalog.tournament-entry
  "Datalevin attributes for the `:tournament-entry` entity — a player's
  registration in a tournament. Mirrors the SQLite `tournament_entry` row
  (`tournament_id`, `player_sub`).

  The entry references its tournament via `:tournament-entry/tournament`;
  navigate from the tournament with the reverse ref
  `:tournament-entry/_tournament`. The SQLite `UNIQUE(tournament_id, player_sub)`
  constraint has no direct Datalevin equivalent — one-entry-per-player is
  enforced in the domain layer at registration time (the same place the
  `:draft` migration enforces its invariants).

  Entries are the source of truth for who participates: standings are
  derived by seeding `rules.tournament/recalculate-standings` with the
  entry player-subs and folding in completed `:match` results.")

(def schema
  {:tournament-entry/eid        {:db/valueType :db.type/uuid
                                 :db/unique    :db.unique/identity}
   :tournament-entry/tournament {:db/valueType :db.type/ref}
   :tournament-entry/player-sub {:db/valueType :db.type/string}
   :tournament-entry/created-at {:db/valueType :db.type/instant}})
