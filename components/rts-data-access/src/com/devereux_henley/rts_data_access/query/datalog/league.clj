(ns com.devereux-henley.rts-data-access.query.datalog.league
  "Datalevin reads and mutations for the league domain. The result
  builders flatten `:league/*` pull results into the unqualified-key
  shape the existing rts-domain handlers + resource schemas expect."
  (:require
   [com.devereux-henley.datalog.contract :as dl])
  (:import
   [java.time Instant]
   [java.util Date]))

;;; ─── Pull patterns ─────────────────────────────────────────────────────────

(def ^:private league-pattern
  [:league/eid :league/name :league/description
   :league/created-by-sub :league/version
   :league/created-at :league/updated-at
   {:league/game [:game/eid :game/name]}])

;;; ─── Result builders ──────────────────────────────────────────────────────

(defn- ->instant
  "Datalevin stores `:db.type/instant` as `java.util.Date`; the resource
  schemas (and the rest of the domain) expect `java.time.Instant`.
  Convert at the read boundary so callers never see Date."
  ^Instant [x]
  (cond
    (nil? x)               nil
    (instance? Instant x)  x
    (instance? Date x)     (.toInstant ^Date x)))

(defn- ->league
  "Flatten a league pull result into the SQLite-era handler-facing
  shape. The `:game` ref sub-map becomes a flat `:game-eid`; the
  `:game/name` join only exists to give `leagues` a sort key and is
  dropped from the output."
  [m]
  (when m
    {:eid            (:league/eid m)
     :game-eid       (some-> m :league/game :game/eid)
     :name           (:league/name m)
     :description    (:league/description m)
     :created-by-sub (:league/created-by-sub m)
     :version        (:league/version m)
     :created-at     (->instant (:league/created-at m))
     :updated-at     (->instant (:league/updated-at m))}))

;;; ─── Reads ────────────────────────────────────────────────────────────────

(defn league-by-eid
  "Fetch a league by eid. Returns nil when not found."
  [conn eid]
  (->league (dl/pull (dl/db conn) league-pattern (dl/lookup-ref :league/eid eid))))

(defn leagues
  "All leagues, sorted by game name then league name — matches the
  legacy SQL ordering so the collection page renders identically."
  [conn]
  (->> (dl/q '[:find [(pull ?l pattern) ...]
               :in $ pattern
               :where [?l :league/eid _]]
             (dl/db conn) league-pattern)
       (sort-by (juxt (fn [m] (some-> m :league/game :game/name))
                      :league/name))
       (mapv ->league)))

(defn leagues-for-game
  "Leagues belonging to a game, sorted by name."
  [conn game-eid]
  (->> (dl/q '[:find [(pull ?l pattern) ...]
               :in $ pattern ?game-eid
               :where
               [?g :game/eid ?game-eid]
               [?l :league/game ?g]]
             (dl/db conn) league-pattern game-eid)
       (sort-by :league/name)
       (mapv ->league)))

;;; ─── Mutations ────────────────────────────────────────────────────────────

(defn- now-date [] (Date.))

(defn create-league!
  "Transact a new league. Audit columns (`:version`, `:created-at`,
  `:updated-at`) are filled here so the handler stays thin."
  [conn {:keys [eid game-eid name description created-by-sub]}]
  (let [now (now-date)]
    (dl/transact!
     conn
     [{:league/eid            eid
       :league/game           [:game/eid game-eid]
       :league/name           name
       :league/description    description
       :league/created-by-sub created-by-sub
       :league/version        1
       :league/created-at     now
       :league/updated-at     now}])
    (league-by-eid conn eid)))
