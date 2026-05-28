(ns com.devereux-henley.rts-data-access.query.datalog.season
  "Datalevin reads and mutations for the season domain. Flattens
  `:season/*` pull results into the unqualified-key shape the
  rts-domain handlers + resource schemas expect."
  (:require
   [com.devereux-henley.datalog.contract :as dl])
  (:import
   [java.util Date]))

;;; ─── Pull patterns ─────────────────────────────────────────────────────────

(def ^:private season-pattern
  [:season/eid :season/ordinal :season/name
   :season/start-at :season/end-at
   :season/version :season/created-at :season/updated-at
   {:season/league [:league/eid :league/name]}])

;;; ─── Result builders ──────────────────────────────────────────────────────

(defn- ->season
  "Flatten a season pull result. `:name` is preserved when present so
  the handler's `display-name` fallback (`\"Season N\"`) can kick in
  when absent."
  [m]
  (when m
    (cond-> {:eid        (:season/eid m)
             :league-eid (some-> m :season/league :league/eid)
             :ordinal    (:season/ordinal m)
             :start-at   (:season/start-at m)
             :end-at     (:season/end-at m)
             :version    (:season/version m)
             :created-at (:season/created-at m)
             :updated-at (:season/updated-at m)}
      (:season/name m) (assoc :name (:season/name m)))))

(defn- epoch-ms ^long [x]
  (cond
    (instance? Date x)              (.getTime ^Date x)
    (instance? java.time.Instant x) (.toEpochMilli ^java.time.Instant x)
    :else                           Long/MIN_VALUE))

;;; ─── Reads ────────────────────────────────────────────────────────────────

(defn season-by-eid
  "Fetch a season by eid. Returns nil when not found."
  [conn eid]
  (->season (dl/pull (dl/db conn) season-pattern (dl/lookup-ref :season/eid eid))))

(defn seasons
  "All seasons, sorted by league name then ordinal — matches the legacy
  SQL ordering."
  [conn]
  (->> (dl/q '[:find [(pull ?s pattern) ...]
               :in $ pattern
               :where [?s :season/eid _]]
             (dl/db conn) season-pattern)
       (sort-by (juxt (fn [m] (some-> m :season/league :league/name))
                      :season/ordinal))
       (mapv ->season)))

(defn seasons-for-league
  "Seasons belonging to a league, sorted by ordinal."
  [conn league-eid]
  (->> (dl/q '[:find [(pull ?s pattern) ...]
               :in $ pattern ?league-eid
               :where
               [?l :league/eid ?league-eid]
               [?s :season/league ?l]]
             (dl/db conn) season-pattern league-eid)
       (sort-by :season/ordinal)
       (mapv ->season)))

(defn current-season-for-league
  "Most recent season whose end-at is still in the future, or nil.
  Filters in Clojure (over a per-league dataset that's typically a
  handful of rows) rather than pushing the instant comparison into the
  query — the comparison sidesteps any Date/Instant coercion mismatch
  between transacted UTC instants and a query-time `now`."
  [conn league-eid]
  (let [now-ms (System/currentTimeMillis)]
    (->> (dl/q '[:find [(pull ?s pattern) ...]
                 :in $ pattern ?league-eid
                 :where
                 [?l :league/eid ?league-eid]
                 [?s :season/league ?l]]
               (dl/db conn) season-pattern league-eid)
         (filter (fn [m] (when-let [end (:season/end-at m)]
                           (>= (epoch-ms end) now-ms))))
         (sort-by (comp - :season/ordinal))
         first
         ->season)))

(defn max-ordinal-for-league
  "Returns `{:max-ordinal n}` with `n=0` when the league has no
  seasons — matches the SQLite-era shape so the handler doesn't have
  to special-case the empty result."
  [conn league-eid]
  (let [m (dl/q '[:find (max ?o) .
                  :in $ ?league-eid
                  :where
                  [?l :league/eid ?league-eid]
                  [?s :season/league ?l]
                  [?s :season/ordinal ?o]]
                (dl/db conn) league-eid)]
    {:max-ordinal (or m 0)}))

;;; ─── Mutations ────────────────────────────────────────────────────────────

(defn- now-date [] (Date.))

(defn- ->date
  "Coerce `x` to `java.util.Date` for storage under `:db.type/instant`,
  which only accepts `Date` (Instants throw a ClassCastException inside
  LMDB encoding). Callers pass either type."
  ^Date [x]
  (cond
    (instance? Date x)              x
    (instance? java.time.Instant x) (Date/from ^java.time.Instant x)))

(defn create-season!
  "Transact a new season under a league. Audit columns are filled here
  so the handler stays thin. The caller has already validated the
  ordinal (next = max+1) and the wall-clock → UTC conversion."
  [conn {:keys [eid league-eid ordinal name start-at end-at]}]
  (let [now (now-date)
        tx  (cond-> {:season/eid        eid
                     :season/league     [:league/eid league-eid]
                     :season/ordinal    ordinal
                     :season/start-at   (->date start-at)
                     :season/end-at     (->date end-at)
                     :season/version    1
                     :season/created-at now
                     :season/updated-at now}
              name (assoc :season/name name))]
    (dl/transact! conn [tx])
    (season-by-eid conn eid)))
