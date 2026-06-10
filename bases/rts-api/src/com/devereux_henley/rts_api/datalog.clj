(ns com.devereux-henley.rts-api.datalog
  "Integrant lifecycle for the Datalevin (LMDB) connection backing all
   persistent state. Opens the store at `dir` with the full data-access
   schema; handlers receive the connection as `:datalog-connection`."
  (:require
   [com.devereux-henley.rts-data-access.contract :as rts-data-access]
   [datalevin.core :as datalevin]
   [integrant.core]))

(def ^:private default-dir "db/datalevin/")

(def dir
  (or (System/getenv "DATALEVIN_DB_DIR") default-dir))

(defmethod integrant.core/init-key ::connection
  [_init-key _dependencies]
  (datalevin/get-conn dir rts-data-access/datalog-schema))

(defmethod integrant.core/halt-key! ::connection
  [_init-key conn]
  (datalevin/close conn))
