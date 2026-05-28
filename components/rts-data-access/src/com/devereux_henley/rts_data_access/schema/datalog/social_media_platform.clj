(ns com.devereux-henley.rts-data-access.schema.datalog.social-media-platform
  "Datalevin attributes for the `:social-media-platform` entity.")

(def schema
  {:social-media-platform/eid          {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :social-media-platform/name         {:db/valueType :db.type/string}
   :social-media-platform/description  {:db/valueType :db.type/string}
   :social-media-platform/platform-url {:db/valueType :db.type/string}})
