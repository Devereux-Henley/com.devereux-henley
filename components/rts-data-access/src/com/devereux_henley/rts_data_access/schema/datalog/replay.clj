(ns com.devereux-henley.rts-data-access.schema.datalog.replay
  "Datalevin attributes for the `:replay` entity — a parsed `.replay`
  file uploaded as part of a tournament match submission.")

(def schema
  {:replay/eid                           {:db/valueType :db.type/uuid
                                          :db/unique    :db.unique/identity}
   :replay/match-id-external             {:db/valueType :db.type/string}
   :replay/played-at                     {:db/valueType :db.type/string}
   :replay/victory-condition             {:db/valueType :db.type/string}
   :replay/parser-format                 {:db/valueType :db.type/string}
   :replay/parsed-data                   {:db/valueType :db.type/string}
   :replay/uploader-local-alliance-index {:db/valueType :db.type/long}
   :replay/uploaded-by-sub               {:db/valueType :db.type/string}
   :replay/created-at                    {:db/valueType :db.type/instant}
   :replay/updated-at                    {:db/valueType :db.type/instant}})
