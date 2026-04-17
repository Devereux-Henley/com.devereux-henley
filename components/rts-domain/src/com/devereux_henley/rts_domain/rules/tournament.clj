(ns com.devereux-henley.rts-domain.rules.tournament)

;; Tournament status lifecycle:
;;   registration → active → complete
;;                ↘ cancelled ←↙

(def valid-transitions
  {"registration" #{"active" "cancelled"}
   "active"       #{"complete" "cancelled"}
   "complete"     #{}
   "cancelled"    #{}})

(defn available-transitions
  "Returns the set of valid target statuses from the given status."
  [current-status]
  (get valid-transitions current-status #{}))

(defn validate-transition
  "Returns nil if the transition is valid, or an error map if not."
  [current-status target-status]
  (let [allowed (get valid-transitions current-status #{})]
    (when-not (contains? allowed target-status)
      {:type    :tournament/transition-error
       :message (str "Cannot transition from '" current-status "' to '" target-status "'.")})))

(defn close-registration
  "Transitions a tournament state from registration to active.
   Populates :standings from the given entries."
  [state entries]
  (-> state
      (assoc :status "active")
      (assoc :standings
             (mapv (fn [entry]
                     {:player-sub (:player-sub entry)
                      :wins       0
                      :losses     0
                      :draws      0
                      :points     0})
                   entries))
      (assoc :current-phase (when (seq (:phases state)) 0))))

(defn recalculate-standings
  "Recalculates standings from a list of completed matches.
   Each win = 3 points, draw = 1 point, loss = 0 points."
  [standings completed-matches]
  (let [results (reduce
                 (fn [acc match]
                   (let [p1 (:player-one-sub match)
                         p2 (:player-two-sub match)
                         winner (:winner-sub match)]
                     (cond
                       (nil? winner) acc
                       (nil? p2) acc ;; bye — no stats change
                       (= winner "draw")
                       (-> acc
                           (update-in [p1 :draws] (fnil inc 0))
                           (update-in [p1 :points] (fnil + 0) 1)
                           (update-in [p2 :draws] (fnil inc 0))
                           (update-in [p2 :points] (fnil + 0) 1))
                       :else
                       (let [loser (if (= winner p1) p2 p1)]
                         (-> acc
                             (update-in [winner :wins] (fnil inc 0))
                             (update-in [winner :points] (fnil + 0) 3)
                             (update-in [loser :losses] (fnil inc 0)))))))
                 (into {} (map (fn [s] [(:player-sub s) (dissoc s :player-sub)])) standings)
                 completed-matches)]
    (mapv (fn [standing]
            (merge standing (get results (:player-sub standing)
                                 {:wins 0 :losses 0 :draws 0 :points 0})))
          standings)))

;; ─── Game completion ─────────────────────────────────────────────────────────

(defn match-win-threshold
  "Number of game wins needed to win a best-of-N match."
  [format]
  (inc (quot format 2)))

(defn check-match-complete
  "Given a list of games and the match format, returns the winner-sub if
   one player has reached the win threshold, or nil if the match is still
   in progress."
  [games format]
  (let [threshold (match-win-threshold format)
        counts    (frequencies (keep :winner-sub games))]
    (some (fn [[player wins]] (when (>= wins threshold) player)) counts)))

;; ─── Swiss pairing ──────────────────────────────────────────────────────────

(defn- played-set
  "Returns a set of #{p1 p2} sets from completed matches."
  [match-history]
  (set (map (fn [m] #{(:player-one-sub m) (:player-two-sub m)}) match-history)))

(defn swiss-pair
  "Generates Swiss pairings from standings and match history.
   Sorts by points descending, pairs adjacent players who haven't played
   each other. Handles odd player count with a bye for the lowest-ranked
   unbye'd player."
  [standings match-history]
  (let [played   (played-set match-history)
        sorted   (->> standings (sort-by :points) reverse vec)
        players  (mapv :player-sub sorted)
        n        (count players)
        ;; Track which players have had byes in this history
        bye-subs (set (keep (fn [m] (when (nil? (:player-two-sub m)) (:player-one-sub m))) match-history))
        ;; If odd, give bye to lowest-ranked player without a prior bye
        [players bye-player]
        (if (odd? n)
          (let [bye-candidate (->> (reverse players)
                                   (remove bye-subs)
                                   first
                                   (or (last players)))]
            [(vec (remove #{bye-candidate} players)) bye-candidate])
          [players nil])
        ;; Pair adjacent players, avoiding repeat matchups
        pairs (loop [remaining players
                     result    []]
                (if (< (count remaining) 2)
                  result
                  (let [p1   (first remaining)
                        rest (vec (rest remaining))
                        ;; Find first opponent p1 hasn't played
                        idx  (or (first (keep-indexed
                                         (fn [i p2]
                                           (when-not (contains? played #{p1 p2}) i))
                                         rest))
                                 0) ;; fallback: pair anyway if all played
                        p2   (nth rest idx)
                        rest (into (subvec rest 0 idx) (subvec rest (inc idx)))]
                    (recur rest (conj result {:player-one-sub p1 :player-two-sub p2})))))]
    (cond-> pairs
      bye-player (conj {:player-one-sub bye-player :player-two-sub nil}))))

;; ─── Elimination bracket ────────────────────────────────────────────────────

(defn generate-elimination-bracket
  "Creates a seeded single-elimination bracket from qualified players.
   Seeds 1 vs N, 2 vs N-1, etc. For non-power-of-2 counts, top seeds
   get first-round byes (nil player-two-sub)."
  [qualified-players]
  (let [n     (count qualified-players)
        ;; Next power of 2
        slots (loop [s 1] (if (>= s n) s (recur (* s 2))))
        ;; Seed order: 1st vs last, 2nd vs 2nd-last, etc.
        matchups (for [i (range (quot slots 2))]
                   (let [seed-a i
                         seed-b (- slots 1 i)
                         p1     (get qualified-players seed-a)
                         p2     (when (< seed-b n) (get qualified-players seed-b))]
                     {:player-one-sub p1 :player-two-sub p2}))]
    (vec matchups)))
