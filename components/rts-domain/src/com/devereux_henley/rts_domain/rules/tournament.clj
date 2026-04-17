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
                   (let [p1     (:player-one-sub match)
                         p2     (:player-two-sub match)
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
        pairs    (loop [remaining players
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
  (let [n        (count qualified-players)
        ;; Next power of 2
        slots    (loop [s 1] (if (>= s n) s (recur (* s 2))))
        ;; Seed order: 1st vs last, 2nd vs 2nd-last, etc.
        matchups (for [i (range (quot slots 2))]
                   (let [seed-a i
                         seed-b (- slots 1 i)
                         p1     (get qualified-players seed-a)
                         p2     (when (< seed-b n) (get qualified-players seed-b))]
                     {:player-one-sub p1 :player-two-sub p2}))]
    (vec matchups)))

;; ─── Double elimination ─────────────────────────────────────────────────────

(defn winners-bracket-round-count
  "Returns the number of winners-bracket rounds for N qualified players:
   ceil(log2 N)."
  [n]
  (loop [s 1 k 0]
    (if (>= s n) k (recur (* s 2) (inc k)))))

(defn losers-bracket-round-count
  "Returns the number of losers-bracket rounds for N qualified players:
   2 * (winners-rounds - 1). Returns 0 for trivial brackets (<=1 player)."
  [n]
  (let [k (winners-bracket-round-count n)]
    (max 0 (* 2 (dec k)))))

(defn winners-from-matches
  "Returns winner-sub of each completed match, in bracket order.
   Bye matches (nil player-two-sub) contribute their auto-winner."
  [matches]
  (mapv :winner-sub (filter #(= "complete" (:status %)) matches)))

(defn- losers-from-matches
  "Returns loser-sub of each completed match that had two real players,
   in bracket order. Bye matches contribute nothing."
  [matches]
  (vec
   (keep (fn [m]
           (when (and (= "complete" (:status m))
                      (:player-two-sub m)
                      (:winner-sub m))
             (if (= (:winner-sub m) (:player-one-sub m))
               (:player-two-sub m)
               (:player-one-sub m))))
         matches)))

(defn- pair-sequentially
  "Pairs adjacent players into matches. Odd player out receives a bye
   (player-two-sub nil)."
  [players]
  (loop [remaining (vec players)
         acc       []]
    (cond
      (empty? remaining) acc
      (= 1 (count remaining))
      (conj acc {:player-one-sub (first remaining) :player-two-sub nil})
      :else
      (recur (subvec remaining 2)
             (conj acc {:player-one-sub (nth remaining 0)
                        :player-two-sub (nth remaining 1)})))))

(defn advance-winners-bracket-round
  "Given matches from the previous winners-bracket round, produces
   pairings for the next winners-bracket round by pairing winners in
   bracket order."
  [prev-round-matches]
  (pair-sequentially (winners-from-matches prev-round-matches)))

(defn generate-losers-bracket-round
  "Generates pairings for losers-bracket round r (0-indexed).

   r = 0             — pair the losers from winners-bracket round 0.
   r odd  (\"major\") — pair losers-bracket winners against losers from
                      the winners-bracket round that just finished.
   r even (>0, \"minor\") — pair losers-bracket winners against each other.

   wb-round-matches is the winners-bracket round whose losers feed this
   losers round (unused for minor rounds). lb-prev-matches is losers
   round r-1 (unused when r=0)."
  [r wb-round-matches lb-prev-matches]
  (cond
    (zero? r)
    (pair-sequentially (losers-from-matches wb-round-matches))

    (even? r)
    (pair-sequentially (winners-from-matches lb-prev-matches))

    :else
    (let [lb-winners (winners-from-matches lb-prev-matches)
          wb-losers  (losers-from-matches wb-round-matches)]
      (mapv (fn [lb wb] {:player-one-sub lb :player-two-sub wb})
            lb-winners wb-losers))))

(defn grand-final-pairing
  "Grand final pairing: winners-bracket champion vs losers-bracket champion."
  [wb-champ lb-champ]
  [{:player-one-sub wb-champ :player-two-sub lb-champ}])

(defn winners-source-round-for-losers-round
  "For a losers-bracket round r (0-indexed), returns the 0-indexed
   winners-bracket round whose losers drop into it, or nil for minor
   losers rounds that don't pull from WB."
  [r]
  (cond
    (zero? r)   0
    (odd?  r)   (quot (inc r) 2)
    :else       nil))

(defn next-ready-double-elim-round
  "Given an immutable snapshot of a double-elimination phase's matches,
   returns a descriptor for the next round whose dependencies are all
   satisfied and that has not yet been generated — or nil if nothing is
   ready. A descriptor is {:bracket :round :pairings}.

   Dependency order: WB-0 (seeded) → WB-N depends on WB-(N-1) complete
   → LB-N depends on LB-(N-1) and (for major rounds) its source WB round
   → grand final depends on both bracket finals complete."
  [phase-matches qualified wb-rounds lb-rounds]
  (let [by-bracket (group-by #(or (:bracket-type %) "winners") phase-matches)
        br-matches (fn [bt r] (filterv #(= r (:round-index %)) (get by-bracket bt [])))
        exists?    (fn [bt r] (seq (br-matches bt r)))
        complete?  (fn [bt r]
                     (let [ms (br-matches bt r)]
                       (and (seq ms) (every? #(= "complete" (:status %)) ms))))]
    (or
     ;; WB round 0: seeded initial bracket.
     (when (and (pos? wb-rounds) (not (exists? "winners" 0)))
       {:bracket  "winners"                                :round 0
        :pairings (generate-elimination-bracket qualified)})
     ;; Subsequent WB rounds: pair previous round's winners.
     (some (fn [r]
             (when (and (not (exists? "winners" r))
                        (complete? "winners" (dec r)))
               {:bracket  "winners"                                                      :round r
                :pairings (advance-winners-bracket-round (br-matches "winners" (dec r)))}))
           (range 1 wb-rounds))
     ;; LB rounds: first drops WB-0 losers; major rounds pull WB losers +
     ;; previous LB winners; minor rounds pair previous LB winners only.
     (some (fn [r]
             (let [wb-src (winners-source-round-for-losers-round r)
                   wb-ok? (or (nil? wb-src) (complete? "winners" wb-src))
                   lb-ok? (or (zero? r) (complete? "losers" (dec r)))]
               (when (and (not (exists? "losers" r)) wb-ok? lb-ok?)
                 {:bracket  "losers"                                        :round r
                  :pairings (generate-losers-bracket-round
                             r
                             (when wb-src (br-matches "winners" wb-src))
                             (when (pos? r) (br-matches "losers" (dec r))))})))
           (range lb-rounds))
     ;; Grand final: one match once both bracket finals are complete.
     (when (and (not (exists? "grand-final" 0))
                (pos? wb-rounds)
                (complete? "winners" (dec wb-rounds))
                (or (zero? lb-rounds) (complete? "losers" (dec lb-rounds))))
       (let [wb-champ (first (winners-from-matches (br-matches "winners" (dec wb-rounds))))
             lb-champ (when (pos? lb-rounds)
                        (first (winners-from-matches (br-matches "losers" (dec lb-rounds)))))]
         (when (and wb-champ (or (zero? lb-rounds) lb-champ))
           {:bracket  "grand-final"                             :round 0
            :pairings (if (pos? lb-rounds)
                        (grand-final-pairing wb-champ lb-champ)
                        [])}))))))

;; ─── Display constants ──────────────────────────────────────────────────────

(def common-timezones
  "Curated list of IANA timezone IDs for the tournament create form."
  ["US/Eastern" "US/Central" "US/Mountain" "US/Pacific"
   "Europe/London" "Europe/Paris" "Europe/Berlin"
   "Asia/Tokyo" "Asia/Shanghai" "Australia/Sydney"
   "UTC"])

(def default-timezone "US/Eastern")

;; ─── Bracket view shape ─────────────────────────────────────────────────────
;; Pure projections over (phases + raw matches) that the HTML view consumes
;; directly. Kept here so the web layer stays thin and so these shapes can be
;; tested without an Integrant system.

(defn- pow2-ceil
  "Smallest power of 2 greater than or equal to n (minimum 1)."
  [n]
  (loop [s 1] (if (>= s n) s (recur (* s 2)))))

(defn expected-winners-round-match-count
  "Match count for winners-bracket round r (0-indexed) seeded from P
   qualifying players: P/2 in round 0, P/4 in round 1, and so on."
  [qualifier-count round-idx]
  (max 1 (quot (quot (pow2-ceil qualifier-count) 2)
               (int (Math/pow 2 round-idx)))))

(defn expected-losers-round-match-count
  "Match count for losers-bracket round r (0-indexed). LB rounds come in
   pairs — minor then major — so rounds 0,1 each have P/4 matches, rounds
   2,3 each have P/8, and so on."
  [qualifier-count round-idx]
  (let [p     (pow2-ceil qualifier-count)
        denom (int (Math/pow 2 (+ (quot round-idx 2) 2)))]
    (max 1 (quot p denom))))

(def ^:private placeholder-match
  "TBD slot produced for rounds that haven't been generated yet."
  {:placeholder    true
   :player-one-sub nil
   :player-two-sub nil
   :winner-sub     nil
   :status         "tbd"})

(defn- build-bracket-rounds
  "Builds a vector of round groupings for one bracket (winners, losers,
   or grand final). Each round is padded with TBD placeholders so the
   skeleton renders even when matches haven't been generated."
  [phase-idx phase-type total-rounds bracket-type bracket-matches round-count expected-count-fn]
  (let [grouped (group-by :round-index bracket-matches)]
    (mapv (fn [round-idx]
            (let [expected     (expected-count-fn round-idx)
                  actual       (get grouped round-idx [])
                  empty-slots  (max 0 (- expected (count actual)))
                  placeholders (repeat empty-slots placeholder-match)]
              {:phase        phase-idx
               :round        round-idx
               :phase-type   phase-type
               :bracket-type bracket-type
               :total-rounds total-rounds
               :matches      (into (vec actual) placeholders)}))
          (range round-count))))

(defn group-matches-by-round
  "Flat, (phase, round)-sorted vector of round groupings. Each entry is
   {:phase :round :phase-type :total-rounds :matches}."
  [raw-matches phases]
  (->> raw-matches
       (group-by (fn [m] {:phase (:phase-index m) :round (:round-index m)}))
       (sort-by (fn [[k _]] [(:phase k) (:round k)]))
       (mapv (fn [[k ms]]
               (let [phase-config (get phases (:phase k))
                     total-rounds (count (:rounds phase-config))]
                 {:phase        (:phase k)
                  :round        (:round k)
                  :phase-type   (:phase-type phase-config)
                  :total-rounds total-rounds
                  :matches      ms})))))

(defn- swiss-phase-rounds
  "Builds the :rounds vector for a non-elimination phase from its own
   matches — sorted by round-index, one entry per round that has matches."
  [phase-idx phase-type total-rounds phase-matches]
  (->> phase-matches
       (group-by :round-index)
       (sort-by first)
       (mapv (fn [[round-idx ms]]
               {:phase        phase-idx
                :round        round-idx
                :phase-type   phase-type
                :total-rounds total-rounds
                :matches      ms}))))

(defn group-matches-by-phase
  "Sequence of phase groupings for the tournament index view.
   Elimination phases expose full bracket skeletons (winners, and for
   double-elim also losers + grand-final). Other phase types expose a
   flat :rounds vector with only rounds that have matches.

   qualifier-count is the expected bracket size; it controls how many
   rounds / matches each bracket skeleton contains."
  [raw-matches phases qualifier-count]
  (let [matches-by-phase-idx (group-by :phase-index raw-matches)]
    (->> (map-indexed vector phases)
         (filter (fn [[idx phase-config]]
                   (or (contains? matches-by-phase-idx idx)
                       (#{"single-elimination" "double-elimination"}
                        (:phase-type phase-config)))))
         (mapv
          (fn [[phase-idx phase-config]]
            (let [phase-type    (:phase-type phase-config)
                  total-rounds  (count (:rounds phase-config))
                  phase-matches (get matches-by-phase-idx phase-idx [])]
              (cond
                (= "single-elimination" phase-type)
                {:phase           phase-idx
                 :phase-type      phase-type
                 :winners-bracket (build-bracket-rounds
                                   phase-idx phase-type total-rounds "winners"
                                   phase-matches total-rounds
                                   #(expected-winners-round-match-count qualifier-count %))}

                (= "double-elimination" phase-type)
                (let [wb-rounds  (winners-bracket-round-count qualifier-count)
                      lb-rounds  (losers-bracket-round-count qualifier-count)
                      by-bracket (group-by #(or (:bracket-type %) "winners") phase-matches)]
                  {:phase           phase-idx
                   :phase-type      phase-type
                   :winners-bracket (build-bracket-rounds
                                     phase-idx phase-type wb-rounds "winners"
                                     (get by-bracket "winners" []) wb-rounds
                                     #(expected-winners-round-match-count qualifier-count %))
                   :losers-bracket  (build-bracket-rounds
                                     phase-idx phase-type lb-rounds "losers"
                                     (get by-bracket "losers" []) lb-rounds
                                     #(expected-losers-round-match-count qualifier-count %))
                   :grand-final     (build-bracket-rounds
                                     phase-idx phase-type 1 "grand-final"
                                     (get by-bracket "grand-final" []) 1 (constantly 1))})

                :else
                {:phase      phase-idx
                 :phase-type phase-type
                 :rounds     (swiss-phase-rounds phase-idx phase-type total-rounds phase-matches)})))))))
