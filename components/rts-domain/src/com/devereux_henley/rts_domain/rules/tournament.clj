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
