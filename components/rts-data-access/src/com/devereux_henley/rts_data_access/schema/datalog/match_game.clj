(ns com.devereux-henley.rts-data-access.schema.datalog.match-game
  "Datalevin attributes for the `:match-game` entity — a single game within
  a best-of-N `:match`. Mirrors the SQLite `match_game` row.

  Games are owned by their match via the cardinality-many `:match/games`
  ref; to find a game's match, pull `:match/_games`.
  `:match-game/game-index` is the 0-based position in the series and
  `:match-game/winner-sub` the Ory subject of the game winner.

  The replay and per-side drafts are now genuine datalog refs
  (`:match-game/replay` → `:replay`, `:match-game/player-one-draft` /
  `…/player-two-draft` → `:draft`). Under SQLite these were integer FKs to
  `draft(id)` / `replay(id)`; once draft (rts-9ri) and replay (rts-23h)
  moved to Datalevin those FKs went permanently NULL. Modelling them as
  refs here restores the link the replay-submission flow already builds.

  `:match-game/uploader-local-alliance-index` records which alliance
  (0/1) the uploading player occupied in the parsed replay, so the
  per-side drafts can be mapped back to the right player.")

(def schema
  {:match-game/eid                           {:db/valueType :db.type/uuid
                                              :db/unique    :db.unique/identity}
   :match-game/game-index                    {:db/valueType :db.type/long}
   :match-game/winner-sub                    {:db/valueType :db.type/string}
   :match-game/replay                        {:db/valueType :db.type/ref}
   :match-game/uploader-local-alliance-index {:db/valueType :db.type/long}
   :match-game/player-one-draft              {:db/valueType :db.type/ref}
   :match-game/player-two-draft              {:db/valueType :db.type/ref}
   :match-game/created-at                    {:db/valueType :db.type/instant}})
