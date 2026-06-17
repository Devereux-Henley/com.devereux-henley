(ns com.devereux-henley.rts-domain.handlers.dispute
  "Domain handlers for the dispute lifecycle — opening a contested-result
  ticket against a match and the organizer resolve/dismiss actions.

  Fetches return nil for a missing dispute; lifecycle functions return the
  tagged dispute on success or a typed `{:type :dispute/error :message …}`
  map on a validation failure, which web handlers dispatch on to choose the
  HTTP status."
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]))

(defn- tag-dispute
  [dispute]
  (when dispute
    (assoc dispute :type :dispute/dispute)))

(defn get-dispute-by-eid
  "Fetches a dispute by eid and tags it with :type :dispute/dispute, or nil."
  [dependencies eid]
  (tag-dispute (db/dispute-by-eid (:datalog-connection dependencies) eid)))

(defn get-open-disputes-for-tournament
  "Returns the open-dispute queue for a tournament — most urgent first — each
  tagged with :type :dispute/dispute."
  [dependencies tournament-eid]
  (mapv tag-dispute (db/open-disputes-for-tournament (:datalog-connection dependencies) tournament-eid)))

(defn get-open-dispute-count-for-tournament
  "Returns the count of open disputes for a tournament — backs the queue-tab
  badge."
  [dependencies tournament-eid]
  (db/open-dispute-count-for-tournament (:datalog-connection dependencies) tournament-eid))

(defn open-dispute
  "Opens a dispute against a match (optionally a specific game) within a
  tournament. Validates that the match exists, belongs to the tournament, and
  that the referenced game belongs to the match. Returns the tagged dispute or
  {:type :dispute/error :message ...}."
  [dependencies {:keys [tournament-eid match-eid match-game-eid] :as spec}]
  (let [conn  (:datalog-connection dependencies)
        match (db/match-by-eid conn match-eid)]
    (cond
      (nil? match)
      {:type :dispute/error :message "Match not found."}

      (not= tournament-eid (:tournament-eid match))
      {:type :dispute/error :message "Match does not belong to this tournament."}

      (and match-game-eid
           (not (some #(= match-game-eid (:eid %)) (db/games-for-match conn match-eid))))
      {:type :dispute/error :message "Game does not belong to this match."}

      :else
      (tag-dispute (db/create-dispute! conn spec)))))

(defn- close-dispute
  "Shared resolve/dismiss path: loads the dispute, guards that it is still
  open, then applies `mutate!`. Returns the tagged dispute or a typed error."
  [dependencies eid mutate!]
  (let [conn    (:datalog-connection dependencies)
        dispute (db/dispute-by-eid conn eid)]
    (cond
      (nil? dispute)
      {:type :dispute/error :message "Dispute not found."}

      (not= "open" (:status dispute))
      {:type :dispute/error :message "Dispute is already resolved or dismissed."}

      :else
      (tag-dispute (mutate! conn eid)))))

(defn resolve-dispute
  "Marks an open dispute resolved. Returns the tagged dispute or
  {:type :dispute/error :message ...}."
  [dependencies eid]
  (close-dispute dependencies eid db/resolve-dispute!))

(defn dismiss-dispute
  "Marks an open dispute dismissed. Returns the tagged dispute or
  {:type :dispute/error :message ...}."
  [dependencies eid]
  (close-dispute dependencies eid db/dismiss-dispute!))
