(ns com.devereux-henley.rts-data-access.query.datalog.dispute
  "Datalevin reads and mutations for the dispute domain — contested match
  results raised against a match (and optionally a specific game) within a
  tournament.

  Reads return flat unqualified-key maps: enum keywords flatten to strings
  (`:dispute/status :open` → `\"open\"`), ref sub-maps become `*-eid` fields,
  and instants are `java.util.Date`. Mutations stamp `opened-at` /
  `resolved-at` server-side as `java.util.Date`."
  (:require
   [com.devereux-henley.datalog.contract :as dl])
  (:import
   [java.util Date]))

;;; ─── Coercions ─────────────────────────────────────────────────────────────

(defn- kw->str
  "Enum keyword → the string the handlers compare against."
  [k]
  (some-> k name))

(def ^:private priority-rank
  "Sort weight for the organizer queue — most urgent first."
  {:urgent 0 :normal 1 :low 2})

;;; ─── Pull pattern + result builder ─────────────────────────────────────────

(def ^:private dispute-pattern
  [:dispute/eid :dispute/kind :dispute/priority :dispute/status
   :dispute/reporter-sub :dispute/detail
   :dispute/opened-at :dispute/resolved-at
   {:dispute/tournament [:tournament/eid]}
   {:dispute/match [:match/eid]}
   {:dispute/match-game [:match-game/eid]}])

(defn- ->dispute
  [m]
  (when m
    {:eid            (:dispute/eid m)
     :tournament-eid (some-> m :dispute/tournament :tournament/eid)
     :match-eid      (some-> m :dispute/match :match/eid)
     :match-game-eid (some-> m :dispute/match-game :match-game/eid)
     :kind           (kw->str (:dispute/kind m))
     :priority       (kw->str (:dispute/priority m))
     :status         (kw->str (:dispute/status m))
     :reporter-sub   (:dispute/reporter-sub m)
     :detail         (:dispute/detail m)
     :opened-at      (:dispute/opened-at m)
     :resolved-at    (:dispute/resolved-at m)}))

;;; ─── Reads ─────────────────────────────────────────────────────────────────

(defn dispute-by-eid
  "Fetch a dispute by eid. Returns nil when not found."
  [conn eid]
  (->dispute (dl/pull (dl/db conn) dispute-pattern (dl/lookup-ref :dispute/eid eid))))

(defn open-disputes-for-tournament
  "All `:open` disputes for a tournament, most urgent first then oldest first
  (priority rank asc, opened-at asc)."
  [conn tournament-eid]
  (let [db (dl/db conn)]
    (->> (dl/q '[:find [(pull ?d pattern) ...]
                 :in $ pattern ?tour-eid
                 :where
                 [?d :dispute/tournament ?t]
                 [?t :tournament/eid ?tour-eid]
                 [?d :dispute/status :open]]
               db dispute-pattern tournament-eid)
         (mapv ->dispute)
         (sort-by (juxt #(get priority-rank (keyword (:priority %)) 1)
                        (comp (fnil #(.getTime ^Date %) (Date. 0)) :opened-at)
                        :eid))
         vec)))

(defn open-dispute-count-for-tournament
  "Count of `:open` disputes for a tournament — backs the queue-tab badge."
  [conn tournament-eid]
  (or (dl/q '[:find (count ?d) .
              :in $ ?tour-eid
              :where
              [?d :dispute/tournament ?t]
              [?t :tournament/eid ?tour-eid]
              [?d :dispute/status :open]]
            (dl/db conn) tournament-eid)
      0))

;;; ─── Mutations ─────────────────────────────────────────────────────────────

(defn create-dispute!
  "Open a dispute in `:open` status, stamping `opened-at`. `:match-game-eid`
  becomes a ref when present (series-level disputes omit it); `:priority`
  defaults to `normal`. Enum-typed `:kind` / `:priority` are stored as
  keywords. Returns the created dispute."
  [conn {:keys [eid tournament-eid match-eid match-game-eid kind priority
                reporter-sub detail]}]
  (let [dispute-eid (or eid (random-uuid))]
    (dl/transact!
     conn
     [(cond-> {:dispute/eid          dispute-eid
               :dispute/tournament   [:tournament/eid tournament-eid]
               :dispute/match        [:match/eid match-eid]
               :dispute/kind         (keyword kind)
               :dispute/priority     (keyword (or priority "normal"))
               :dispute/status       :open
               :dispute/reporter-sub reporter-sub
               :dispute/opened-at    (Date.)}
        match-game-eid (assoc :dispute/match-game [:match-game/eid match-game-eid])
        detail         (assoc :dispute/detail detail))])
    (dispute-by-eid conn dispute-eid)))

(defn resolve-dispute!
  "Mark a dispute `:resolved`, stamping `resolved-at`. Returns the updated
  dispute."
  [conn eid]
  (dl/transact!
   conn
   [{:dispute/eid         eid
     :dispute/status      :resolved
     :dispute/resolved-at (Date.)}])
  (dispute-by-eid conn eid))

(defn dismiss-dispute!
  "Mark a dispute `:dismissed`, stamping `resolved-at`. Returns the updated
  dispute."
  [conn eid]
  (dl/transact!
   conn
   [{:dispute/eid         eid
     :dispute/status      :dismissed
     :dispute/resolved-at (Date.)}])
  (dispute-by-eid conn eid))
