(ns com.devereux-henley.rts-data-deploy.core
  (:require
   [migratus.core :as migratus])
  (:gen-class))

(def ^:private migration-dir "rts-data/migrations")

(defn- db-spec []
  {:connection-uri (or (System/getenv "RTS_DB_CONNECTION_URI")
                       "jdbc:sqlite:db/database.db")})

(defn- config []
  {:store         :database
   :migration-dir migration-dir
   :db            (db-spec)})

(defn -main [& args]
  (case (first args)
    "migrate"  (migratus/migrate (config))
    "rollback" (migratus/rollback (config))
    (migratus/migrate (config))))
