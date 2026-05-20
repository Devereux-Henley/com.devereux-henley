(ns com.devereux-henley.rts-demo.core
  "Bootstraps a fresh SQLite database with three demo tournaments — one
  single-elimination, one double-elimination, one Swiss — each populated
  with eight entrants and advanced to the last-match state so the new
  tournament viewer always has a non-trivial bracket to render.

  Run from the REPL via `(setup!)` or as a one-shot CLI via
  `clojure -M:dev:claude -m com.devereux-henley.rts-demo.core`."
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

(def ^:private player-subs
  (mapv #(str "dev-player-" %)
        ["one" "two" "three" "four" "five" "six" "seven" "eight"]))

(defn- delete-database-file!
  []
  (when (= "sqlite" (:dbtype jdbc-spec))
    (let [file (io/file (:dbname jdbc-spec))]
      (when (.exists file)
        (.delete file)))))

(defn- create-tournament!
  [connection {:keys [eid name description]}]
  (let [now       (Instant/now)
        opens-at  (LocalDateTime/now)
        closes-at (.plusDays opens-at 14)
        zone      (ZoneId/of "UTC")]
    (data-access/create-tournament
     connection
     {:eid            eid
      :game-eid       warhammer-iii-eid
      :name           name
      :description    description
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

(defn- enter-players!
  [deps tournament-eid players]
  (doseq [sub players]
    (enter-player! deps tournament-eid sub)))

(defn- configure-phases!
  [deps tournament-eid phase-spec]
  (let [result (domain/configure-phases deps tournament-eid phase-spec organizer-sub)]
    (when (not= :tournament/phase-configured (:type result))
      (throw (ex-info "Phase configuration failed" {:result result})))))

(defn- start!
  [deps tournament-eid]
  (let [result (domain/start-tournament deps tournament-eid organizer-sub)]
    (when (not= :tournament/started (:type result))
      (throw (ex-info "Starting tournament failed" {:result result})))))

(defn- generate-round!
  "Calls generate-next-round, returning the result map. Caller decides
   what to do with an error response."
  [deps tournament-eid]
  (domain/generate-next-round deps tournament-eid organizer-sub))

(defn- resolve-match!
  "Records the minimum games needed for player-one to win the match.
   Going through record-game-result (rather than update-match-result)
   leaves real match_game rows behind so the bracket viewer can show
   per-player game counts."
  [deps match]
  (let [wins-needed (inc (quot (:format match) 2))
        winner      (:player-one-sub match)]
    (dotimes [_ wins-needed]
      (domain/record-game-result deps (:eid match) winner))))

(defn- single-elim-done?
  "Stop when the only pending match is the bracket final (a sole pending
   match in a single-elim tournament is always the final)."
  [pending _all-matches _state]
  (= 1 (count pending)))

(defn- double-elim-done?
  "Stop when the grand-final match has been generated. Anything else
   pending (LB/WB intermediate rounds) still needs to be resolved."
  [_pending all-matches _state]
  (boolean
   (some #(and (= "grand-final" (:bracket-type %))
               (= "pending" (:status %)))
         all-matches)))

(defn- swiss-done?
  "Stop when the last configured round has been generated and still has
   pending matches. The Swiss demo wants a fresh final round on display."
  [pending all-matches state]
  (let [phase-idx    (:current-phase state)
        phase        (get-in state [:phases phase-idx])
        total-rounds (count (:rounds phase))]
    (boolean
     (and (seq pending)
          (seq all-matches)
          (= (apply max (map :round-index all-matches))
             (dec total-rounds))))))

(def ^:private stop-predicates
  {"single-elimination" single-elim-done?
   "double-elimination" double-elim-done?
   "swiss"              swiss-done?})

(defn- advance-to-last-match!
  "Drives a tournament forward by alternately resolving pending matches
   and generating the next round, until the type-specific stop predicate
   says we're at the final/decisive match state. Returns the final
   pending-match list."
  [deps tournament-eid phase-type]
  (let [stop? (or (stop-predicates phase-type)
                  (throw (ex-info "Unknown phase type" {:phase-type phase-type})))]
    (loop [safety 50]
      (when (zero? safety)
        (throw (ex-info "advance-to-last-match! exceeded safety limit"
                        {:tournament-eid tournament-eid})))
      (let [all-matches (domain/get-matches-for-tournament deps tournament-eid)
            pending     (filter #(= "pending" (:status %)) all-matches)
            state       (domain/get-tournament-state deps tournament-eid)]
        (cond
          (stop? pending all-matches state)
          pending

          (empty? pending)
          (let [result (generate-round! deps tournament-eid)]
            (if (= :tournament/round-generated (:type result))
              (recur (dec safety))
              (throw (ex-info "generate-next-round failed before reaching last-match state"
                              {:result result}))))

          :else
          (do
            (run! #(resolve-match! deps %) pending)
            (recur (dec safety))))))))

(def ^:private tournament-specs
  [{:eid         #uuid "11111111-1111-4111-8111-111111111111"
    :name        "Crown of Karak Eight Peaks"
    :description "Eight warlords contest the throne in a classic single-elimination bracket."
    :phase-spec  {:phases          [{:phase-type "single-elimination"
                                     :rounds     [{:format 5} {:format 5} {:format 5}]}]
                  :qualifier-count 8}}
   {:eid         #uuid "22222222-2222-4222-8222-222222222222"
    :name        "Siege of Naggaroth"
    :description "Double-elimination — losers get a second life, the winners' champion gets to wait in the grand final."
    :phase-spec  {:phases          [{:phase-type "double-elimination"
                                     :rounds     [{:format 3} {:format 3} {:format 3}]}]
                  :qualifier-count 8}}
   {:eid         #uuid "33333333-3333-4333-8333-333333333333"
    :name        "Empire Open"
    :description "Three-round Swiss. Every entrant plays every round; no one is eliminated until the final tally."
    :phase-spec  {:phases          [{:phase-type "swiss"
                                     :rounds     [{:format 1} {:format 1} {:format 1}]}]
                  :qualifier-count 4}}])

(def ^:private registration-tournament-specs
  [{:eid              #uuid "44444444-4444-4444-8444-444444444444"
    :name             "Wolves of Norsca Cup"
    :description      "Open registration — first eight to sign on take the bracket."
    :initial-entrants 3}])

(defn- build-tournament!
  [connection {:keys [eid name description phase-spec]}]
  (let [deps           {:connection connection}
        phase-type     (-> phase-spec :phases first :phase-type)
        tournament-eid (create-tournament! connection {:eid eid :name name :description description})]
    (enter-players! deps tournament-eid player-subs)
    (configure-phases! deps tournament-eid phase-spec)
    (start! deps tournament-eid)
    (let [final-pending (advance-to-last-match! deps tournament-eid phase-type)]
      {:name           name
       :phase-type     phase-type
       :tournament-eid tournament-eid
       :pending-eids   (mapv :eid final-pending)})))

(defn- build-registration-tournament!
  "Creates a tournament left in `registration` status with a partial
   roster so the viewer's registration UI has a live demo target."
  [connection {:keys [eid name description initial-entrants]}]
  (let [deps           {:connection connection}
        tournament-eid (create-tournament! connection {:eid eid :name name :description description})
        roster         (take initial-entrants player-subs)]
    (enter-players! deps tournament-eid roster)
    {:name             name
     :phase-type       "registration"
     :tournament-eid   tournament-eid
     :pending-eids     []
     :registered-count (count roster)}))

(defn setup!
  "Wipes the SQLite database, reapplies migrations, seeds baseline data,
  and creates demo tournaments: three advanced to their last-match
  state (single-elim, double-elim, Swiss), plus one left in open
  registration. Returns a vector of summary maps describing each
  tournament."
  []
  (delete-database-file!)
  (migratus/migrate migratus-config)
  (rts-data/seed-db db-spec)
  (with-open [connection (jdbc/get-connection jdbc-spec)]
    (into (mapv #(build-tournament! connection %) tournament-specs)
          (mapv #(build-registration-tournament! connection %) registration-tournament-specs))))

(defn -main [& _args]
  (let [tournaments (setup!)]
    (println "Demo tournaments ready.")
    (println "  game-eid:" warhammer-iii-eid)
    (doseq [{:keys [name phase-type tournament-eid pending-eids registered-count]} tournaments]
      (println)
      (println (format "  %s (%s)" name phase-type))
      (println (format "    tournament-eid: %s" tournament-eid))
      (when registered-count
        (println (format "    registered:     %d of %d slots" registered-count (count player-subs))))
      (doseq [match-eid pending-eids]
        (println (format "    pending match:  %s" match-eid))))))
