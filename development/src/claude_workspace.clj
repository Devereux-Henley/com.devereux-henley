(ns claude-workspace
  "Claude Code dev helpers. Loaded via the :dev alias alongside workspace.clj."
  (:require
   [com.devereux-henley.rts-api.configuration :as configuration]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [com.devereux-henley.rts-api.db :as rts-db]
   [integrant.repl :refer [go halt reset]]
   [migratus.core :as migratus]))

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
  (seed-db!))
