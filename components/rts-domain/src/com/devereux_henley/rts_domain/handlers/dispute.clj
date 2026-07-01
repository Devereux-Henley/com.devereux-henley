(ns com.devereux-henley.rts-domain.handlers.dispute
  "Domain handlers for the dispute lifecycle — opening a contested-result
  ticket against a match and the organizer resolve/dismiss actions.

  Fetches return nil for a missing dispute; lifecycle functions return the
  tagged dispute on success or a typed `{:type :dispute/error :message …}`
  map on a validation failure, which web handlers dispatch on to choose the
  HTTP status."
  (:require
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.rules.tournament :as rules]))

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

(defn- load-open-dispute
  "Loads a dispute and guards that it is still open. Returns the dispute on
  success or a typed `{:type :dispute/error :message …}` map."
  [conn eid]
  (let [dispute (db/dispute-by-eid conn eid)]
    (cond
      (nil? dispute)                  {:type :dispute/error :message "Dispute not found."}
      (not= "open" (:status dispute)) {:type :dispute/error :message "Dispute is already resolved or dismissed."}
      :else                           dispute)))

(defn resolve-dispute
  "Resolves an open dispute by recording the corrected per-side game-win score
  the organizer entered. Validates that the dispute is open and that the score
  is a completed best-of-N result for the disputed match — the winning side has
  exactly the win threshold and the loser fewer — then derives the winner from
  the score, marks the match complete with that winner, and stamps the
  resolution (derived winner, per-side score, optional note) onto the dispute.
  Returns the tagged dispute or {:type :dispute/error :message ...}."
  [dependencies eid {:keys [player-one-score player-two-score] :as resolution}]
  (let [conn    (:datalog-connection dependencies)
        dispute (load-open-dispute conn eid)]
    (if (= :dispute/error (:type dispute))
      dispute
      (let [match     (db/match-by-eid conn (:match-eid dispute))
            threshold (some-> match :format rules/match-win-threshold)]
        (cond
          (nil? match)
          {:type :dispute/error :message "Disputed match not found."}

          (not (and (= threshold (max player-one-score player-two-score))
                    (< (min player-one-score player-two-score) threshold)))
          {:type    :dispute/error
           :message (format "Result must be a completed best-of-%d: the winner needs exactly %d game wins and the loser fewer."
                            (:format match) threshold)}

          :else
          (let [winner-sub (if (= player-one-score threshold)
                             (:player-one-sub match)
                             (:player-two-sub match))]
            (db/update-match-result! conn (:match-eid dispute) winner-sub)
            (tag-dispute (db/resolve-dispute! conn eid (assoc resolution :winner-sub winner-sub)))))))))

(defn dismiss-dispute
  "Marks an open dispute dismissed — the no-action path, recording no result.
  Returns the tagged dispute or {:type :dispute/error :message ...}."
  [dependencies eid]
  (let [conn    (:datalog-connection dependencies)
        dispute (load-open-dispute conn eid)]
    (if (= :dispute/error (:type dispute))
      dispute
      (tag-dispute (db/dismiss-dispute! conn eid)))))
