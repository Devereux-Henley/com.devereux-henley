(ns claude-workspace
  "Claude Code dev helpers. Loaded via the :dev alias alongside workspace.clj."
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rts-api.configuration :as configuration]
   [com.devereux-henley.rts-api.datalog :as rts-datalog]
   [com.devereux-henley.rts-api.db :as rts-db]
   [com.devereux-henley.rts-api.dev-auth :as dev-auth]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [integrant.core]
   [integrant.repl :refer [go halt reset]]
   [migratus.core :as migratus]
   [selmer.parser]))

;; Disable Selmer template caching at namespace load so REPL-driven dev picks
;; up template edits without needing a manual cache clear. Production code
;; paths re-enable caching via their own startup.
(selmer.parser/cache-off!)

;; Default the REPL to the development profile (Ory auth stubbed out with
;; cookie-session impersonation). Boots the same system `workspace.clj` would,
;; but stays self-contained so Claude can drive the system from this namespace
;; alone.
(integrant.repl/set-prep!
 (fn [] (integrant.core/expand configuration/development-configuration)))

;; -- System helpers ----------------------------------------------------------

(defn go!
  "Start the system (runs migrations + starts Jetty on :3001)."
  []
  (go))

(defn halt!
  "Stop the system."
  []
  (halt))

(defn restart!
  "Halt then restart the system."
  []
  (reset))

;; -- Migration helpers -------------------------------------------------------

(def migratus-config
  {:store         :database
   :migration-dir rts-data/migration-dir
   :db            rts-db/db-spec})

(defn migrate! [] (migratus/migrate migratus-config))
(defn rollback! [] (migratus/rollback migratus-config))
(defn reset-db! [] (migratus/reset migratus-config))
(defn seed-sqlite! [] (rts-data/seed-db rts-db/db-spec))

;; -- Datalog helpers ---------------------------------------------------------

(defn- delete-recursively!
  "Depth-first delete; tolerates a missing directory."
  [^java.io.File f]
  (when (.exists f)
    (doseq [child (reverse (file-seq f))]
      (.delete ^java.io.File child))))

(defn reset-datalog!
  "Halt the system, wipe the Datalevin LMDB directory, and restart so the
   conn re-opens against an empty store. Use when the schema changed in a
   non-additive way (rename/retract attribute) or when a fresh fixture state
   is needed."
  []
  (halt)
  (delete-recursively! (io/file rts-datalog/dir))
  (go))

(defn seed-datalog!
  "Seed the Datalevin store with the canonical dev dataset. No-op while the
   schema is empty; per-domain epics extend this as they migrate off SQLite."
  []
  nil)

;; -- Impersonation helpers ---------------------------------------------------

(def dev-users
  "Predefined dev users available in the development profile."
  dev-auth/dev-users)

(defn impersonation-cookie
  "Header value Claude can send on clj-http/Jetty requests to act as the given
   dev user. Example: `{:headers {\"cookie\" (impersonation-cookie \"dev-admin\")}}`."
  [user-id]
  (str dev-auth/cookie-name "=" user-id))

;; -- Scratch -----------------------------------------------------------------

(comment
  ;; System lifecycle
  (go!)
  (halt!)
  (restart!)

  ;; SQLite migrations
  (migrate!)
  (rollback!)
  (reset-db!)
  (seed-sqlite!)

  ;; Datalog
  (reset-datalog!)
  (seed-datalog!)

  ;; Impersonation (development profile only)
  (keys dev-users)
  (impersonation-cookie "dev-admin"))
