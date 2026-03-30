(ns com.devereux-henley.rts-data.contract
  (:require
   [com.devereux-henley.rts-data.migrations :as migrations]
   [integrant.core]))

(def migration-dir "rts-data/migrations")

(defmethod integrant.core/init-key ::migrate
  [_init-key opts]
  (migrations/migrate! opts)
  opts)

(defmethod integrant.core/halt-key! ::migrate
  [_halt-key _state]
  nil)
