(ns com.devereux-henley.rts-data-access.schema.datalog.replay
  "Datalevin attributes for the `:replay` entity — a parsed `.replay`
  file uploaded as part of a tournament match submission.

  `:replay/parsed-data` is an idoc holding the parser's full output
  (alliances, armies, per-unit keys, spells, etc.). Storing as a
  document keeps the schema stable as the parser evolves; queries that
  need to reach into individual fields use `datalevin/idoc-match`
  / `idoc-get`.")

(def schema
  {:replay/eid                           {:db/valueType :db.type/uuid
                                          :db/unique    :db.unique/identity}
   :replay/match-id-external             {:db/valueType :db.type/string}
   :replay/played-at                     {:db/valueType :db.type/string}
   :replay/victory-condition             {:db/valueType :db.type/string}
   :replay/parser-format                 {:db/valueType :db.type/string}
   :replay/parsed-data                   {:db/valueType  :db.type/idoc
                                          :db/idocFormat :json}
   :replay/uploader-local-alliance-index {:db/valueType :db.type/long}
   :replay/uploaded-by-sub               {:db/valueType :db.type/string}
   :replay/created-at                    {:db/valueType :db.type/instant}
   :replay/updated-at                    {:db/valueType :db.type/instant}})
