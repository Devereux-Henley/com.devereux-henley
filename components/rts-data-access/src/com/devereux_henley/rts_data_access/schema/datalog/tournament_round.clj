(ns com.devereux-henley.rts-data-access.schema.datalog.tournament-round
  "Datalevin attributes for the `:tournament-round` entity — one round
  within a `:tournament-phase`. `:round-index` is its position (0-based);
  `:format` is the best-of count every match in the round inherits.")

(def schema
  {:tournament-round/eid         {:db/valueType :db.type/uuid
                                  :db/unique    :db.unique/identity}
   :tournament-round/round-index {:db/valueType :db.type/long}
   :tournament-round/format      {:db/valueType :db.type/long}})
