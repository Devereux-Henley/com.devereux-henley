(ns com.devereux-henley.rts-demo.core
  "Bootstraps a fresh SQLite database with a tournament that's ready for
  replay uploads. Run from the REPL via `(setup!)` or as a one-shot CLI
  via `clojure -M:dev:claude -m com.devereux-henley.rts-demo.core`.

  After completion the system has:
   - migrations applied + baseline game data seeded
   - one tournament organized by `dev-admin`, status `active`, single-elim bo3
   - two entries (`dev-player-one`, `dev-player-two`)
   - one round-1 match wired to both players, ready for `/match/:eid/record`."
  (:require
   [clojure.java.io :as io]
   [com.devereux-henley.rts-data-access.contract :as data-access]
   [com.devereux-henley.rts-data.contract :as rts-data]
   [com.devereux-henley.rts-domain.contract :as domain]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc])
  (:import
   [java.time Instant LocalDateTime ZoneId])
  (:gen-class))

(def ^:private default-connection-uri "jdbc:sqlite:db/database.db")

(def ^:private connection-uri
  (or (System/getenv "RTS_DB_CONNECTION_URI") default-connection-uri))

(def ^:private db-spec
  {:connection-uri connection-uri})

(def ^:private jdbc-spec
  {:dbtype "sqlite"
   :dbname (subs connection-uri (count "jdbc:sqlite:"))})

(def ^:private migratus-config
  {:store         :database
   :migration-dir rts-data/migration-dir
   :db            db-spec})

(def ^:private warhammer-iii-eid
  #uuid "eea787d7-1065-45eb-a3f6-e26f32c294a1")

(def ^:private organizer-sub "dev-admin")
(def ^:private player-one-sub "dev-player-one")
(def ^:private player-two-sub "dev-player-two")

(defn- delete-database-file!
  []
  (when (= "sqlite" (:dbtype jdbc-spec))
    (let [file (io/file (:dbname jdbc-spec))]
      (when (.exists file)
        (.delete file)))))

(defn- create-tournament!
  [connection]
  (let [eid       (random-uuid)
        now       (Instant/now)
        opens-at  (LocalDateTime/now)
        closes-at (.plusDays opens-at 14)
        zone      (ZoneId/of "UTC")]
    (data-access/create-tournament
     connection
     {:eid            eid
      :game-eid       warhammer-iii-eid
      :name           "Replay Demo Cup"
      :description    "Seeded tournament for exercising the replay upload flow."
      :created-by-sub organizer-sub
      :version        1
      :created-at     now
      :updated-at     now})
    (domain/set-tournament-state
     {:connection connection}
     eid
     {:status          "registration"
      :registration    {:opens-at     (str (.toInstant (.atZone opens-at zone)))
                        :closes-at    (str (.toInstant (.atZone closes-at zone)))
                        :timezone     (str zone)
                        :closed-early false}
      :phases          []
      :current-phase   nil
      :standings       []
      :qualifier-count nil})
    eid))

(defn- enter-player!
  [deps tournament-eid player-sub]
  (let [result (domain/create-entry deps tournament-eid player-sub)]
    (when (= :tournament/entry-error (:type result))
      (throw (ex-info (str "Failed to enter " player-sub ": " (:message result))
                      {:player-sub player-sub :result result})))
    result))

(defn- configure-and-start!
  [deps tournament-eid]
  (let [phase-result (domain/configure-phases
                      deps
                      tournament-eid
                      {:phases          [{:phase-type "single-elimination"
                                          :rounds     [{:format 3}]}]
                       :qualifier-count 2}
                      organizer-sub)]
    (when (not= :tournament/phase-configured (:type phase-result))
      (throw (ex-info "Phase configuration failed" {:result phase-result}))))
  (let [start-result (domain/start-tournament deps tournament-eid organizer-sub)]
    (when (not= :tournament/started (:type start-result))
      (throw (ex-info "Starting tournament failed" {:result start-result}))))
  (let [round-result (domain/generate-next-round deps tournament-eid organizer-sub)]
    (when (not= :tournament/round-generated (:type round-result))
      (throw (ex-info "Round generation failed" {:result round-result})))
    round-result))

(defn setup!
  "Wipes the SQLite database, reapplies migrations, seeds baseline data,
  and creates an active tournament with a round-1 match. Returns a map
  with the tournament eid and the eid of the first match."
  []
  (delete-database-file!)
  (migratus/migrate migratus-config)
  (rts-data/seed-db db-spec)
  (with-open [connection (jdbc/get-connection jdbc-spec)]
    (let [deps           {:connection connection}
          tournament-eid (create-tournament! connection)]
      (enter-player! deps tournament-eid player-one-sub)
      (enter-player! deps tournament-eid player-two-sub)
      (let [{:keys [matches]} (configure-and-start! deps tournament-eid)]
        {:tournament-eid tournament-eid
         :game-eid       warhammer-iii-eid
         :match-eids     (mapv :eid matches)}))))

(defn -main [& _args]
  (let [{:keys [tournament-eid game-eid match-eids]} (setup!)]
    (println "Demo tournament ready.")
    (println "  game-eid:      " game-eid)
    (println "  tournament-eid:" tournament-eid)
    (doseq [match-eid match-eids]
      (println "  match-eid:     " match-eid))
    (println)
    (println "Open the post-match modal at:")
    (doseq [match-eid match-eids]
      (println (format "  /view/match-record/%s/index.html" match-eid)))))
