(ns com.devereux-henley.rts-api.datalog
  "Integrant lifecycle for the Datalevin (LMDB) connection. Coexists with
   `db/connection` (SQLite) for the duration of the storage migration; the
   key will become the sole store once all domains are migrated and the
   SQLite ref is removed."
  (:require
   [datalevin.core :as datalevin]
   [integrant.core]))

(def ^:private default-dir "db/datalevin/")

(def dir
  (or (System/getenv "DATALEVIN_DB_DIR") default-dir))

(def schema
  "Datalevin schema-as-code. Empty for now; per-domain attributes get merged
   in by the schema-as-code namespace (rts-8z4) as each domain migrates."
  {})

(defmethod integrant.core/init-key ::connection
  [_init-key _dependencies]
  (datalevin/get-conn dir schema))

(defmethod integrant.core/halt-key! ::connection
  [_init-key conn]
  (datalevin/close conn))
