(ns workspace
  (:require
   [com.devereux-henley.rts-api.configuration :as configuration]
   [com.devereux-henley.rts-api.system :as system]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [com.devereux-henley.rts-api.db :as rts-db]
   [integrant.repl :refer [go halt reset]]
   [migratus.core :as migratus]))

(integrant.repl/set-prep! (fn [] configuration/core-configuration))

(def migratus-config
  {:store         :database
   :migration-dir rts-data/migration-dir
   :db            rts-db/db-spec})

(comment
  (go)
  (halt)
  (reset)
  (system/reload-views!)
  (migratus/migrate migratus-config)
  (migratus/rollback migratus-config)
  (migratus/reset migratus-config)
  (rts-data/seed-db rts-db/db-spec))
