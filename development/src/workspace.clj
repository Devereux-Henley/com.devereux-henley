(ns workspace
  (:require
   [com.devereux-henley.rts-api.system :as system]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [com.devereux-henley.rts-api.db :as rts-db]
   [migratus.core :as migratus]))

(def migratus-config
  {:store         :database
   :migration-dir rts-data/migration-dir
   :db            rts-db/db-spec})

(comment
  (system/go!)
  (system/halt!)
  (system/reload-views!)
  (system/restart!)
  (migratus/migrate migratus-config)
  (migratus/rollback migratus-config)
  (migratus/reset migratus-config)
  (rts-data/seed-db rts-db/db-spec))
