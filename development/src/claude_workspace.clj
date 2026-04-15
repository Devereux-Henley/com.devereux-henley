(ns claude-workspace
  "Claude Code dev helpers. Loaded via the :dev alias alongside workspace.clj."
  (:require
   [com.devereux-henley.rts-api.configuration :as configuration]
   [com.devereux-henley.rts-api.dev-auth :as dev-auth]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [com.devereux-henley.rts-api.db :as rts-db]
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
(defn seed-db! [] (rts-data/seed-db rts-db/db-spec))

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

  ;; Migrations
  (migrate!)
  (rollback!)
  (reset-db!)
  (seed-db!)

  ;; Impersonation (development profile only)
  (keys dev-users)
  (impersonation-cookie "dev-admin"))
