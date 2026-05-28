(ns com.devereux-henley.rts-data-access.schema.datalog.game-mode
  "Datalevin attributes for the `:game-mode` entity (per-game playlist —
  budget, player count, reinforcement rules).")

(def schema
  {:game-mode/eid                    {:db/valueType :db.type/uuid
                                      :db/unique    :db.unique/identity}
   :game-mode/name                   {:db/valueType :db.type/string}
   :game-mode/description            {:db/valueType :db.type/string}
   :game-mode/draft-value            {:db/valueType :db.type/long}
   :game-mode/player-count           {:db/valueType :db.type/long}
   :game-mode/reinforcement-value    {:db/valueType :db.type/long}
   :game-mode/reinforcements-enabled {:db/valueType :db.type/boolean}
   :game-mode/game                   {:db/valueType :db.type/ref}})
