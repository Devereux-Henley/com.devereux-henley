(ns com.devereux-henley.rts-api.db
  (:require
   [clojure.string :as str]
   [integrant.core]
   [next.jdbc :as jdbc]))

(def ^:private default-connection-uri "jdbc:sqlite:db/database.db")

(def ^:private connection-uri
  (or (System/getenv "RTS_DB_CONNECTION_URI") default-connection-uri))

(def db
  {:dbtype "sqlite"
   :dbname (str/replace connection-uri #"^jdbc:sqlite:" "")})

(def db-spec
  {:connection-uri connection-uri})

(defmethod integrant.core/init-key ::connection
  [_init-key _dependencies]
  (jdbc/get-connection db))
