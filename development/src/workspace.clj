(ns workspace
  (:require
   [com.devereux-henley.rts-api.configuration :as configuration]
   [com.devereux-henley.rts-api.db :as rts-db]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [integrant.core]
   [integrant.repl :refer [go halt reset]]
   [migratus.core :as migratus]))

(integrant.repl/set-prep! (fn [] (integrant.core/expand configuration/development-configuration)))

(def migratus-config
  {:store         :database
   :migration-dir rts-data/migration-dir
   :db            rts-db/db-spec})

(comment
  (go)
  (halt)
  (reset)
  (migratus/migrate migratus-config)
  (migratus/rollback migratus-config)
  (migratus/reset migratus-config)
  (rts-data/seed-db rts-db/db-spec))
