(ns com.devereux-henley.rts-data.migrations
  (:require
   [migratus.core :as migratus]))

(defn migrate! [{:keys [db-spec migration-dir]}]
  (migratus/migrate {:store         :database
                     :migration-dir migration-dir
                     :db            db-spec}))
