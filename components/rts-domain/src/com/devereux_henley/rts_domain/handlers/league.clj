(ns com.devereux-henley.rts-domain.handlers.league
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.handlers.season :as handlers.season]
   [com.devereux-henley.rts-domain.handlers.stats :as handlers.stats]))

(defn get-league-by-eid
  "Fetches a league by eid and tags it with :type :league/league."
  [dependencies eid]
  (some-> (db/league-by-eid (:datalog-connection dependencies) eid)
          (assoc :type :league/league)))

(defn get-leagues-for-game
  "Returns all leagues for a game, each tagged with :type :league/league."
  [dependencies game-eid]
  (mapv #(assoc % :type :league/league)
        (db/leagues-for-game (:datalog-connection dependencies) game-eid)))

(defn get-leagues
  "Returns every league in the system, each tagged with :type :league/league."
  [dependencies]
  (mapv #(assoc % :type :league/league)
        (db/leagues (:datalog-connection dependencies))))

(defn create-league
  "Creates a new league. Audit columns (`:version`, `:created-at`,
  `:updated-at`) are stamped in the data-access layer."
  [dependencies create-specification]
  (-> (db/create-league! (:datalog-connection dependencies) create-specification)
      (assoc :type :league/league)))

(defn league-view-model
  "Builds the league detail view-model: the league entity under `:data`, its
   seasons, scoped tournaments, faction standings, and whether `viewer-sub`
   owns the league. Returns a `:missing/resource` marker when the league
   doesn't exist."
  [dependencies eid viewer-sub]
  (if-let [league (db/league-by-eid (:datalog-connection dependencies) eid)]
    (let [conn        (:datalog-connection dependencies)
          seasons     (handlers.season/get-seasons-for-league dependencies eid)
          eid->season (into {} (map (juxt :eid identity)) seasons)
          tournaments (->> (db/tournaments-for-game conn (:game-eid league))
                           (filterv #(= eid (:league-eid %)))
                           (mapv (fn [tournament]
                                   (cond-> tournament
                                     (:season-eid tournament)
                                     (assoc :season-display-name
                                            (get-in eid->season [(:season-eid tournament) :display-name]))))))]
      {:data         (assoc league :type :league/league)
       :seasons      seasons
       :tournaments  tournaments
       :standings    (handlers.stats/get-league-faction-standings dependencies eid)
       :is-organizer (= viewer-sub (:created-by-sub league))})
    {:type :missing/resource :name "league" :id eid}))
