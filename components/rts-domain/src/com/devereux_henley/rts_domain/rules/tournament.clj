(ns com.devereux-henley.rts-domain.rules.tournament)

;; Tournament status lifecycle:
;;   registration → active → complete
;;                ↘ cancelled ←↙

(def valid-transitions
  {"registration" #{"active" "cancelled"}
   "active"       #{"complete" "cancelled"}
   "complete"     #{}
   "cancelled"    #{}})

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
