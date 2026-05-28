(ns com.devereux-henley.datalog.contract
  "Thin wrapper around Datalevin to provide a single seam for schema decode/encode
  and to enforce the rule that domain namespaces never call `datalevin.core` directly.
  Mirrors the role that `com.devereux-henley.jdbc.contract` plays for SQLite."
  (:require
   [datalevin.core :as datalevin]))

(defn get-conn
  "Open (or reuse) a Datalevin connection at `dir` with the given `schema`.
   `datalevin/get-conn` is idempotent: the schema merges into any pre-existing
   one, so additive schema changes apply on restart without rewriting LMDB state."
  ([dir]
   (datalevin/get-conn dir))
  ([dir schema]
   (datalevin/get-conn dir schema)))

(defn close
  "Close a Datalevin connection. Safe to call from Integrant `halt-key!`."
  [conn]
  (datalevin/close conn))

(defn q
  "Run a datalog query. `query` is a datalog form (vector or map); `inputs` are the
   sources/bindings (typically the db plus any parameters)."
  [query & inputs]
  (apply datalevin/q query inputs))

(defn pull
  "Pull `pattern` for the entity identified by `eid-or-lookup-ref` from `db-or-conn`.
   Accepts either a numeric entity id, a lookup ref like
   `[:tournament/eid #uuid \"...\"]`, or a connection (in which case the
   connection's current db value is used)."
  [db-or-conn pattern eid-or-lookup-ref]
  (datalevin/pull
   (if (datalevin/conn? db-or-conn) (datalevin/db db-or-conn) db-or-conn)
   pattern
   eid-or-lookup-ref))

(defn transact!
  "Apply `tx-data` to `conn`. Returns the transaction report."
  ([conn tx-data]
   (datalevin/transact! conn tx-data))
  ([conn tx-data tx-meta]
   (datalevin/transact! conn tx-data tx-meta)))

(defn lookup-ref
  "Build a lookup ref vector `[attr value]`. Convention: every domain has a
   `:<domain>/eid` attribute of `:db.unique/identity`, so
   `(lookup-ref :tournament/eid uuid)` resolves any entity by its public uuid."
  [attr value]
  [attr value])

(defn entity
  "Return the `Entity` map for `eid-or-lookup-ref` from `db-or-conn`. Use for
   ad-hoc navigation; prefer `pull` when the shape is known and bounded."
  [db-or-conn eid-or-lookup-ref]
  (datalevin/entity
   (if (datalevin/conn? db-or-conn) (datalevin/db db-or-conn) db-or-conn)
   eid-or-lookup-ref))
