(ns com.devereux-henley.rts-data-deploy.core
  (:require
   [com.devereux-henley.rts-data.contract :as rts-data]
   [migratus.core :as migratus])
  (:gen-class))

(defn- db-spec []
  {:connection-uri (or (System/getenv "RTS_DB_CONNECTION_URI")
                       "jdbc:sqlite:db/database.db")})

(defn- config []
  {:store         :database
   :migration-dir rts-data/migration-dir
   :db            (db-spec)})

(defn -main [& args]
  (case (first args)
    "migrate"  (migratus/migrate (config))
    "rollback" (migratus/rollback (config))
    (migratus/migrate (config))))
