(ns com.devereux-henley.rts-data-access.schema.datalog.match-game
  "Datalevin attributes for the `:match-game` entity — a single game within
  a best-of-N `:match`, owned via `:match/games`. `:game-index` is its
  0-based position in the series. `:match-game/replay` refs the parsed
  `:replay`; `:player-one-draft` / `:player-two-draft` ref the per-side
  `:draft`s. `:uploader-local-alliance-index` records which alliance (0/1)
  the uploader played, mapping the per-side drafts back to each player.

  `:status` tracks the per-game verification gate: a submitted game is
  `:pending-confirmation` until the non-uploading player confirms
  (`:confirmed`) or disputes it (`:disputed`). `:submitted-by-sub` is the
  uploader; `:confirmed-by-sub` / `:confirmed-at` record who accepted the
  result (the literal `\"auto\"` when the `:confirm-deadline` lapses).")

(def schema
  {:match-game/eid                           {:db/valueType :db.type/uuid
                                              :db/unique    :db.unique/identity}
   :match-game/game-index                    {:db/valueType :db.type/long}
   :match-game/winner-sub                    {:db/valueType :db.type/string}
   :match-game/replay                        {:db/valueType :db.type/ref}
   :match-game/uploader-local-alliance-index {:db/valueType :db.type/long}
   :match-game/player-one-draft              {:db/valueType :db.type/ref}
   :match-game/player-two-draft              {:db/valueType :db.type/ref}
   :match-game/created-at                    {:db/valueType :db.type/instant}
   :match-game/status                        {:db/valueType :db.type/keyword}
   :match-game/submitted-by-sub              {:db/valueType :db.type/string}
   :match-game/confirmed-by-sub              {:db/valueType :db.type/string}
   :match-game/confirmed-at                  {:db/valueType :db.type/instant}
   :match-game/confirm-deadline              {:db/valueType :db.type/instant}})
