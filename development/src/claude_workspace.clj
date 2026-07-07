(ns claude-workspace
  "Claude Code dev helpers. Loaded via the :dev alias alongside workspace.clj."
  (:require
   [clojure.java.io :as io]
   [datalevin.core :as datalevin]
   [com.devereux-henley.rts-api.configuration :as configuration]
   [com.devereux-henley.rts-api.datalog :as rts-datalog]
   [com.devereux-henley.rts-api.dev-auth :as dev-auth]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [integrant.core]
   [integrant.repl :refer [go halt reset]]
   [integrant.repl.state :as ig-state]
   [malli.dev :as malli.dev]
   [selmer.parser]))

;; Turn on malli function-schema instrumentation in the dev REPL so any
;; `m/=>`-declared fn validates its inputs/outputs as you exercise it.
;; `malli.dev/start!` re-instruments after each var change, which keeps
;; `(restart!)`-driven reloads honest. Production paths never reach this
;; namespace, so prod stays uninstrumented.
(malli.dev/start!)

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
  "Start the system (opens the Datalevin connection + starts Jetty on :3001)."
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

(def default-patch-version
  "Patch the dev workspace seeds against by default. Bump when a new
   patch's EDN seed has been published under
   `components/rts-data/resources/rts-data/seed/datalog/<version>/`."
  "8.0")

(defn seed-datalog!
  "Transact every EDN seed file for `patch-version` into the running
   Datalevin store (requires the system to be up — call `(go!)` first).
   Idempotent: every entity carries `:db.unique/identity`, so re-runs
   upsert in place. Throws if no seed files exist for the requested
   version so a typo doesn't silently transact nothing."
  ([] (seed-datalog! default-patch-version))
  ([patch-version]
   (let [conn (get ig-state/system :com.devereux-henley.rts-api.datalog/connection)]
     (when-not conn
       (throw (ex-info "seed-datalog! requires the system to be running — call (go!) first" {})))
     (rts-data/ensure-datalog-patch patch-version)
     (println (format "Seeding Datalevin from patch %s" patch-version))
     (doseq [[file-name tx-data] (rts-data/load-datalog-seed patch-version)]
       (datalevin/transact! conn tx-data)
       (println (format "  %-30s %d entities" file-name (count tx-data))))
     (println "Done."))))

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

  ;; Datalog
  (reset-datalog!)
  (seed-datalog!)
  (seed-datalog! "8.0")

  ;; Impersonation (development profile only)
  (keys dev-users)
  (impersonation-cookie "dev-admin"))
