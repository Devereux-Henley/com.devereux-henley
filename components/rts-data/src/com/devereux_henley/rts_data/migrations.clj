(ns com.devereux-henley.rts-data.migrations
  (:require
   [integrant.core]
   [migratus.core :as migratus]))

(defmethod integrant.core/init-key ::migrate
  [_init-key {:keys [db-spec migration-dir]}]
  (migratus/migrate {:store         :database
                     :migration-dir migration-dir
                     :db            db-spec})
  {:db-spec db-spec :migration-dir migration-dir})

(defmethod integrant.core/halt-key! ::migrate
  [_halt-key _state]
  nil)
