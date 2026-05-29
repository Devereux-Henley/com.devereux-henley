(ns com.devereux-henley.rts-data-access.schema.datalog.tournament-entry
  "Datalevin attributes for the `:tournament-entry` entity — a player's
  registration in a tournament, referencing it via
  `:tournament-entry/tournament`. One entry per player is enforced in the
  domain layer at registration time.")

(def schema
  {:tournament-entry/eid        {:db/valueType :db.type/uuid
                                 :db/unique    :db.unique/identity}
   :tournament-entry/tournament {:db/valueType :db.type/ref}
   :tournament-entry/player-sub {:db/valueType :db.type/string}
   :tournament-entry/created-at {:db/valueType :db.type/instant}})
