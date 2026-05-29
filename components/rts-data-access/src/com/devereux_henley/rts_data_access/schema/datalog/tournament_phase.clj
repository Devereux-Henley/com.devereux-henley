(ns com.devereux-henley.rts-data-access.schema.datalog.tournament-phase
  "Datalevin attributes for the `:tournament-phase` entity — one stage of a
  tournament's format. `:ordinal` is its position in the tournament (the
  phase index). `:phase-type` is a keyword enum `#{:swiss :round-robin
  :single-elimination :double-elimination}`. Owns its rounds via the
  cardinality-many `:tournament-phase/rounds`.")

(def schema
  {:tournament-phase/eid        {:db/valueType :db.type/uuid
                                 :db/unique    :db.unique/identity}
   :tournament-phase/ordinal    {:db/valueType :db.type/long}
   :tournament-phase/phase-type {:db/valueType :db.type/keyword}
   :tournament-phase/rounds     {:db/valueType   :db.type/ref
                                 :db/cardinality :db.cardinality/many}})
