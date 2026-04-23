(ns com.devereux-henley.rts-domain.handlers.season
  (:require
   [com.devereux-henley.rts-data-access.contract :as db])
  (:import
   [java.time Instant LocalDateTime ZoneId]))

(defn- to-utc-instant
  "Converts a LocalDateTime in the given ZoneId to a UTC Instant."
  ^Instant [^LocalDateTime local-dt ^ZoneId zone]
  (-> local-dt (.atZone zone) .toInstant))

(defn- display-name
  "Derives the display name for a season — explicit :name override or 'Season N'."
  [season]
  (or (:name season) (str "Season " (:ordinal season))))

(defn- tag-season
  [season]
  (when season
    (-> season
        (assoc :type :season/season)
        (assoc :display-name (display-name season)))))

(defn get-season-by-eid
  "Fetches a season by eid and tags it with :type :season/season + :display-name."
  [dependencies eid]
  (tag-season (db/get-season-by-eid (:connection dependencies) eid)))

(defn get-seasons-for-league
  "Returns all seasons for a league, each tagged with :type :season/season + :display-name."
  [dependencies league-eid]
  (mapv tag-season (db/get-seasons-for-league (:connection dependencies) league-eid)))

(defn get-current-season-for-league
  "Returns the most recent season whose end_at is still in the future, or nil."
  [dependencies league-eid]
  (tag-season (db/get-current-season-for-league (:connection dependencies) league-eid)))

(defn create-season
  "Creates a season under a league. Auto-assigns ordinal as MAX(ordinal)+1.
   Validates that the caller owns the league and that start-at < end-at.
   Returns the created season or {:type :season/error :message ...}."
  [dependencies {:keys [eid league-eid name timezone start-at end-at] :as _spec} player-sub]
  (let [conn   (:connection dependencies)
        league (db/get-league-by-eid conn league-eid)]
    (cond
      (nil? league)
      {:type :season/error :message "League not found."}

      (not= player-sub (:created-by-sub league))
      {:type :season/error :message "Only the league owner can create seasons."}

      :else
      (let [start-instant (to-utc-instant start-at timezone)
            end-instant   (to-utc-instant end-at timezone)]
        (if-not (.isBefore start-instant end-instant)
          {:type :season/error :message "Season start must be before end."}
          (let [now                        (Instant/now)
                {max-ordinal :max-ordinal} (db/get-max-ordinal-for-league conn league-eid)
                next-ordinal               (inc max-ordinal)
                season                     (db/create-season
                                            conn
                                            (cond-> {:eid        (or eid (random-uuid))
                                                     :league-eid league-eid
                                                     :ordinal    next-ordinal
                                                     :start-at   start-instant
                                                     :end-at     end-instant
                                                     :version    1
                                                     :created-at now
                                                     :updated-at now}
                                              name (assoc :name name)))]
            (tag-season season)))))))
