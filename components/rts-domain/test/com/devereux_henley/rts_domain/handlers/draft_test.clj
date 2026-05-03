(ns com.devereux-henley.rts-domain.handlers.draft-test
  (:require
   [clojure.string]
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.draft :as handlers.draft]
   [jsonista.core])
  (:import
   [java.time Instant]
   [java.util UUID]))

(defn- state-json
  "Builds a draft-state JSON string from maps of {:main [uuid …] :reinforcements [uuid …]}.
   Each unit-eid slot accepts either a bare uuid (a fresh entry-eid is generated)
   or a two-vector [unit-eid entry-eid] when the test needs to target the entry
   by a stable id."
  [{:keys [main reinforcements]}]
  (letfn [(normalize [slot] (if (vector? slot) slot [slot (UUID/randomUUID)]))
          (entry [[uid eeid]]
            (str "{\"entry-eid\":\"" eeid "\",\"unit-eid\":\"" uid
                 "\",\"mount\":null,\"spells\":[],\"items\":[],\"total-cost\":null}"))]
    (str "{\"main\":["
         (clojure.string/join "," (map (comp entry normalize) main))
         "],\"reinforcements\":["
         (clojure.string/join "," (map (comp entry normalize) reinforcements)) "]}")))

(def ^:private test-draft-eid    (UUID/fromString "d0000000-0000-0000-0000-000000000001"))
(def ^:private test-unit-eid     (UUID/fromString "c0000000-0000-0000-0000-000000000001"))
(def ^:private test-lord-eid     (UUID/fromString "c0000000-0000-0000-0000-000000000002"))
(def ^:private test-faction-eid  (UUID/fromString "f0000000-0000-0000-0000-000000000001"))
(def ^:private test-game-mode-eid (UUID/fromString "a0000000-0000-0000-0000-000000000001"))
(def ^:private test-player-sub   "auth0|test-player")
(def ^:private test-deps         {:connection nil})

(def ^:private test-draft
  {:eid           test-draft-eid
   :game-mode-eid test-game-mode-eid
   :faction-eid   test-faction-eid
   :player-sub    test-player-sub})

(def ^:private test-game-mode
  {:eid                    test-game-mode-eid
   :draft-value            1000
   :reinforcement-value    500
   :reinforcements-enabled 1})

(def ^:private infantry-unit
  {:eid                test-unit-eid
   :name               "Swordsmen"
   :cost               100
   :unit-category-name "Infantry"
   :unit-statistics    "{}"})

(def ^:private lord-unit
  {:eid                test-lord-eid
   :name               "Karl Franz"
   :cost               200
   :unit-category-name "Lord"
   :unit-statistics    "{}"})

;; --- parse-unit-statistics ---

(deftest parse-unit-statistics-extracts-stats
  (let [json   "{\"leadership\":75,\"speed\":30,\"armor\":25}"
        result (handlers.draft/parse-unit-statistics json)]
    (is (= 3 (count (:stats result))))))

(deftest parse-unit-statistics-excludes-reserved-keys
  (let [json   "{\"leadership\":75,\"abilities\":[\"Frenzy\"],\"mounts\":[{\"name\":\"Horse\",\"mp_cost\":50}]}"
        result (handlers.draft/parse-unit-statistics json)]
    (is (every? #(not= "abilities" (:stat %)) (:stats result)))
    (is (every? #(not= "mounts" (:stat %)) (:stats result)))))

(deftest parse-unit-statistics-parses-abilities
  (let [json   "{\"abilities\":[\"Frenzy\",\"Fear\"]}"
        result (handlers.draft/parse-unit-statistics json)]
    (is (= ["Frenzy" "Fear"] (:abilities result)))))

(deftest parse-unit-statistics-omits-zero-values
  (let [json   "{\"charge bonus\":0,\"leadership\":50}"
        result (handlers.draft/parse-unit-statistics json)]
    (is (= 1 (count (:stats result))))
    (is (= "leadership" (:stat (first (:stats result)))))))

(deftest parse-unit-statistics-omits-empty-vectors
  (let [json   "{\"abilities\":[],\"leadership\":50}"
        result (handlers.draft/parse-unit-statistics json)]
    (is (= 1 (count (:stats result))))))

;; --- hydrate-units-with-stats ---

(deftest hydrate-units-with-stats-sets-is-lord-true-for-lord-category
  (let [result (handlers.draft/hydrate-units-with-stats [lord-unit])]
    (is (true? (:is-lord (first result))))))

(deftest hydrate-units-with-stats-sets-is-lord-false-for-non-lord
  (let [result (handlers.draft/hydrate-units-with-stats [infantry-unit])]
    (is (false? (:is-lord (first result))))))

(deftest hydrate-units-with-stats-preserves-unit-fields
  (let [result (handlers.draft/hydrate-units-with-stats [infantry-unit])]
    (is (= test-unit-eid (:eid (first result))))
    (is (= "Swordsmen" (:name (first result))))))

(deftest hydrate-units-with-stats-empty-input
  (is (= [] (handlers.draft/hydrate-units-with-stats []))))

;; --- group-units-by-family ---

(def ^:private faction-eid #uuid "f0000005-0000-4000-8000-000000000000")

(deftest group-units-by-family-collapses-marked-variants
  (let [units  [{:id   1               :eid         #uuid "00000001-0000-4000-8000-000000000000"
                 :name "Daemon Prince" :faction-eid faction-eid                                  :mark nil :cost 1800}
                {:id   2               :eid         #uuid "00000002-0000-4000-8000-000000000000"
                 :name "Daemon Prince" :faction-eid faction-eid                                  :mark "khorne" :cost 1900}
                {:id   3               :eid         #uuid "00000003-0000-4000-8000-000000000000"
                 :name "Daemon Prince" :faction-eid faction-eid                                  :mark "tzeentch" :cost 1900}]
        result (handlers.draft/group-units-by-family units)
        family (first result)]
    (is (= 1 (count result)))
    (is (= "Daemon Prince" (:name family)))
    (is (nil? (:mark family))
        "Canonical prefers the unmarked variant when present")
    (is (= 3 (count (:family-variants family))))
    (is (= [nil "khorne" "tzeentch"]
           (mapv :mark (:family-variants family)))
        "Variants ordered by id (stable across renders)")))

(deftest group-units-by-family-preserves-single-variant-families
  (let [units  [{:id   10                       :eid         #uuid "0000000a-0000-4000-8000-000000000000"
                 :name "Bloodletters of Khorne" :faction-eid faction-eid                                  :mark "khorne" :cost 800}]
        result (handlers.draft/group-units-by-family units)
        family (first result)]
    (is (= 1 (count result)))
    (is (= "khorne" (:mark family))
        "Canonical falls back to the only variant when no unmarked exists")
    (is (= 1 (count (:family-variants family))))))

(deftest group-units-by-family-groups-by-family-name-when-engine-names-differ
  ;; Mark variants carry distinct engine names ("Daemon Prince of Khorne",
  ;; etc.) but share `:family-name` "Daemon Prince" so the roster card
  ;; still collapses them into one family.
  (let [units  [{:id   1               :eid         #uuid "00000001-0000-4000-8000-000000000000"
                 :name "Daemon Prince" :family-name "Daemon Prince"                              :faction-eid faction-eid
                 :mark "undivided"     :cost        1800}
                {:id   2                         :eid         #uuid "00000002-0000-4000-8000-000000000000"
                 :name "Daemon Prince of Khorne" :family-name "Daemon Prince"                              :faction-eid faction-eid
                 :mark "khorne"                  :cost        1900}
                {:id   3                           :eid         #uuid "00000003-0000-4000-8000-000000000000"
                 :name "Daemon Prince of Tzeentch" :family-name "Daemon Prince"                              :faction-eid faction-eid
                 :mark "tzeentch"                  :cost        1900}]
        result (handlers.draft/group-units-by-family units)
        family (first result)]
    (is (= 1 (count result)))
    (is (= "Daemon Prince" (:name family))
        "Canonical prefers the row whose engine name matches family-name")
    (is (= 3 (count (:family-variants family))))
    (is (= ["Daemon Prince" "Daemon Prince of Khorne" "Daemon Prince of Tzeentch"]
           (mapv :name (:family-variants family)))
        "Each variant carries its engine-original name through to the panel")))

(deftest group-units-by-family-keeps-different-faction-rows-separate
  (let [other-faction #uuid "f0000017-0000-4000-8000-000000000000"
        units         [{:id   1               :eid         #uuid "00000001-0000-4000-8000-000000000000"
                        :name "Daemon Prince" :faction-eid faction-eid                                  :mark "khorne" :cost 1900}
                       {:id   2               :eid         #uuid "00000002-0000-4000-8000-000000000000"
                        :name "Daemon Prince" :faction-eid other-faction                                :mark "khorne" :cost 1900}]
        result        (handlers.draft/group-units-by-family units)]
    (is (= 2 (count result))
        "Same display name in different factions are different families")))

;; --- build-section-context ---

(deftest build-section-context-sums-unit-costs
  (let [units [{:cost 100 :is-lord false} {:cost 250 :is-lord false}]
        ctx   (handlers.draft/build-section-context "main" units test-draft-eid test-game-mode)]
    (is (= 350 (:section-cost ctx)))))

(deftest build-section-context-uses-draft-value-for-main
  (let [ctx (handlers.draft/build-section-context "main" [] test-draft-eid test-game-mode)]
    (is (= 1000 (:section-max ctx)))))

(deftest build-section-context-uses-reinforcement-value-for-reinforcements
  (let [ctx (handlers.draft/build-section-context "reinforcements" [] test-draft-eid test-game-mode)]
    (is (= 500 (:section-max ctx)))))

(deftest build-section-context-sets-over-budget-when-cost-exceeds-max
  (let [units [{:cost 1100 :is-lord false}]
        ctx   (handlers.draft/build-section-context "main" units test-draft-eid test-game-mode)]
    (is (true? (:section-over-budget ctx)))))

(deftest build-section-context-not-over-budget-when-within-limit
  (let [units [{:cost 100 :is-lord false}]
        ctx   (handlers.draft/build-section-context "main" units test-draft-eid test-game-mode)]
    (is (false? (:section-over-budget ctx)))))

(deftest build-section-context-separates-lord-from-non-lords
  (let [hydrated (handlers.draft/hydrate-units-with-stats [lord-unit infantry-unit])
        ctx      (handlers.draft/build-section-context "main" hydrated test-draft-eid test-game-mode)]
    (is (some? (:lord-unit ctx)))
    (is (= 1 (count (:non-lord-units ctx))))))

(deftest build-section-context-sets-is-main-flag
  (is (true? (:is-main (handlers.draft/build-section-context "main" [] test-draft-eid test-game-mode))))
  (is (false? (:is-main (handlers.draft/build-section-context "reinforcements" [] test-draft-eid test-game-mode)))))

(deftest build-section-context-attaches-draft-eid
  (let [ctx (handlers.draft/build-section-context "main" [] test-draft-eid test-game-mode)]
    (is (= test-draft-eid (:draft-eid ctx)))))

(deftest build-section-context-not-over-budget-when-game-mode-nil
  (let [ctx (handlers.draft/build-section-context "main" [{:cost 99999 :is-lord false}] test-draft-eid nil)]
    (is (false? (:section-over-budget ctx)))))

;; --- get-draft-by-eid ---

(deftest get-draft-by-eid-assigns-type
  (with-redefs [data-access.contract/get-draft-by-eid (fn [_ _] test-draft)]
    (is (= :game/draft (:type (handlers.draft/get-draft-by-eid test-deps test-draft-eid))))))

(deftest get-draft-by-eid-preserves-fields
  (with-redefs [data-access.contract/get-draft-by-eid (fn [_ _] test-draft)]
    (let [result (handlers.draft/get-draft-by-eid test-deps test-draft-eid)]
      (is (= test-draft-eid (:eid result)))
      (is (= test-faction-eid (:faction-eid result)))
      (is (= test-game-mode-eid (:game-mode-eid result))))))

;; --- create-draft ---

(deftest create-draft-assigns-type
  (with-redefs [data-access.contract/create-draft (fn [_ spec] spec)]
    (is (= :game/draft (:type (handlers.draft/create-draft test-deps test-draft))))))

(deftest create-draft-injects-created-at-timestamp
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/create-draft (fn [_ spec] (reset! captured spec) spec)]
      (handlers.draft/create-draft test-deps test-draft)
      (is (instance? Instant (:created-at @captured))))))

(deftest create-draft-injects-updated-at-timestamp
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/create-draft (fn [_ spec] (reset! captured spec) spec)]
      (handlers.draft/create-draft test-deps test-draft)
      (is (instance? Instant (:updated-at @captured))))))

(deftest create-draft-created-at-equals-updated-at
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/create-draft (fn [_ spec] (reset! captured spec) spec)]
      (handlers.draft/create-draft test-deps test-draft)
      (is (= (:created-at @captured) (:updated-at @captured))))))

;; --- display name ---

(def ^:private named-draft
  (assoc test-draft
         :name               "Teclis Expeditionary Force"
         :faction-name       "High Elves"
         :created-at-display "04/21/2026"))

(def ^:private unnamed-draft
  (assoc test-draft
         :name               nil
         :faction-name       "High Elves"
         :created-at-display "04/21/2026"))

(deftest get-draft-by-eid-uses-custom-name-for-display
  (with-redefs [data-access.contract/get-draft-by-eid (fn [_ _] named-draft)]
    (is (= "Teclis Expeditionary Force"
           (:display-name (handlers.draft/get-draft-by-eid test-deps test-draft-eid))))))

(deftest get-draft-by-eid-falls-back-to-faction-date-default
  (with-redefs [data-access.contract/get-draft-by-eid (fn [_ _] unnamed-draft)]
    (is (= "High Elves draft 04/21/2026"
           (:display-name (handlers.draft/get-draft-by-eid test-deps test-draft-eid))))))

(deftest get-draft-by-eid-treats-blank-name-as-default
  (with-redefs [data-access.contract/get-draft-by-eid (fn [_ _] (assoc unnamed-draft :name "   "))]
    (is (= "High Elves draft 04/21/2026"
           (:display-name (handlers.draft/get-draft-by-eid test-deps test-draft-eid))))))

(deftest get-drafts-for-player-by-game-attaches-display-name-to-each
  (with-redefs [data-access.contract/get-drafts-for-player-by-game
                (fn [_ _ _] [named-draft unnamed-draft])]
    (let [results (handlers.draft/get-drafts-for-player-by-game test-deps test-player-sub (UUID/randomUUID))]
      (is (= ["Teclis Expeditionary Force" "High Elves draft 04/21/2026"]
             (mapv :display-name results))))))

;; --- update-draft ---

(deftest update-draft-persists-trimmed-name-and-recomputes-display
  (let [captured (atom nil)]
    (with-redefs [data-access.contract/update-draft
                  (fn [_ _eid updates]
                    (reset! captured updates)
                    (assoc unnamed-draft :name (:name updates)))]
      (let [result (handlers.draft/update-draft test-deps test-draft-eid {:name "  Avelorn Corps  "})]
        (is (= "Avelorn Corps" (:name @captured)))
        (is (= "Avelorn Corps" (:display-name result)))
        (is (= :game/draft (:type result)))))))

(deftest update-draft-with-empty-name-clears-the-column
  (let [captured (atom :sentinel)]
    (with-redefs [data-access.contract/update-draft
                  (fn [_ _eid updates]
                    (reset! captured updates)
                    (assoc unnamed-draft :name nil))]
      (let [result (handlers.draft/update-draft test-deps test-draft-eid {:name "   "})]
        (is (nil? (:name @captured)))
        (is (= "High Elves draft 04/21/2026" (:display-name result)))))))

;; --- get-drafts-for-player ---

(deftest get-drafts-for-player-assigns-type-to-each-draft
  (with-redefs [data-access.contract/get-drafts-for-player (fn [_ _] [test-draft {:eid (UUID/randomUUID)}])]
    (let [results (handlers.draft/get-drafts-for-player test-deps test-player-sub)]
      (is (every? #(= :game/draft (:type %)) results)))))

(deftest get-drafts-for-player-empty-result
  (with-redefs [data-access.contract/get-drafts-for-player (fn [_ _] [])]
    (is (= [] (handlers.draft/get-drafts-for-player test-deps test-player-sub)))))

;; --- get-drafts-for-player-by-game ---

(deftest get-drafts-for-player-by-game-assigns-type-to-each-draft
  (let [game-eid (UUID/randomUUID)]
    (with-redefs [data-access.contract/get-drafts-for-player-by-game (fn [_ _ _] [test-draft])]
      (let [results (handlers.draft/get-drafts-for-player-by-game test-deps test-player-sub game-eid)]
        (is (every? #(= :game/draft (:type %)) results))))))

(deftest get-drafts-for-player-by-game-empty-result
  (let [game-eid (UUID/randomUUID)]
    (with-redefs [data-access.contract/get-drafts-for-player-by-game (fn [_ _ _] [])]
      (is (= [] (handlers.draft/get-drafts-for-player-by-game test-deps test-player-sub game-eid))))))

;; --- get-draft-state ---

(deftest get-draft-state-returns-empty-when-no-state-exists
  (with-redefs [data-access.contract/get-draft-state-by-draft (fn [_ _] nil)]
    (let [result (handlers.draft/get-draft-state test-deps test-draft-eid)]
      (is (= [] (:main result)))
      (is (= [] (:reinforcements result))))))

(deftest get-draft-state-parses-new-entry-format
  (let [uid (UUID/randomUUID)]
    (with-redefs [data-access.contract/get-draft-state-by-draft
                  (fn [_ _] {:state (str "{\"main\":[{\"unit-eid\":\"" uid
                                         "\",\"mount\":\"Barded Warhorse\",\"spells\":[]"
                                         ",\"items\":[],\"total-cost\":1350}]"
                                         ",\"reinforcements\":[]}")})]
      (let [result (handlers.draft/get-draft-state test-deps test-draft-eid)
            entry  (first (:main result))]
        (is (= uid (:unit-eid entry)))
        (is (= "Barded Warhorse" (:mount entry)))
        (is (= 1350 (:total-cost entry)))))))

;; --- add-unit-to-draft ---

(def ^:private test-level-costs
  "Subset of the unit_level_cost seed sufficient for level 0/1 in tests.
   Level 0 is a no-op (multiplier 1.0, fixed 0). Level 1 covers the
   leveled-add path: base 100 → round(100 * 1.03) + 11 = 114."
  {0 {:level 0 :fixed-cost 0 :cost-multiplier 1.0 :fatigue 0 :melee-cp 0.0 :missile-cp 0.0}
   1 {:level 1 :fixed-cost 11 :cost-multiplier 1.03 :fatigue 0 :melee-cp 26.0 :missile-cp 26.0}})

(defn- stub-add-unit
  "Returns a fixture fn for add-unit-to-draft tests.
   faction-units  — list returned by get-units-for-faction.
   existing-state-json — :state string for get-draft-state-by-draft, or nil."
  ([faction-units existing-state-json]
   (stub-add-unit faction-units existing-state-json {}))
  ([faction-units existing-state-json {:keys [mounts items abilities level-costs]
                                       :or   {mounts [] items [] abilities {} level-costs test-level-costs}}]
   (fn [f]
     (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                   data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                   data-access.contract/get-draft-state-by-draft   (fn [_ _] (when existing-state-json {:state existing-state-json}))
                   data-access.contract/get-units-for-faction      (fn [_ _] faction-units)
                   data-access.contract/get-spells-by-keys         (fn [_ _] {})
                   data-access.contract/get-abilities-by-keys      (fn [_ _] abilities)
                   data-access.contract/get-items-for-unit         (fn [_ _] items)
                   data-access.contract/get-mounts-for-unit        (fn [_ _] mounts)
                   data-access.contract/get-unit-level-costs       (fn [_] level-costs)
                   data-access.contract/get-family-variants-by-eid (fn [_ _] [])
                   data-access.contract/upsert-draft-state         (fn [_ _ _] nil)]
       (f)))))

(deftest add-unit-to-draft-returns-error-when-unit-not-in-faction
  ((stub-add-unit [] nil)
   (fn []
     (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main" {})]
       (is (= :draft/add-error (:type result)))
       (is (string? (:message result)))))))

(deftest add-unit-to-draft-returns-error-when-over-budget
  ((stub-add-unit [{:eid test-unit-eid :name "Giant" :cost 1100 :unit-category-name "Infantry" :unit-statistics "{}"}] nil)
   (fn []
     (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main" {})]
       (is (= :draft/add-error (:type result)))))))

(deftest add-unit-to-draft-returns-error-when-lord-already-in-main
  (let [existing-state (state-json {:main [test-lord-eid] :reinforcements []})]
    ((stub-add-unit [infantry-unit lord-unit
                     {:eid                (UUID/fromString "c0000000-0000-0000-0000-000000000003")
                      :name               "Archaon"
                      :cost               200
                      :unit-category-name "Lord"
                      :unit-statistics    "{}"}]
                    existing-state)
     (fn []
       (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid
                                                      (UUID/fromString "c0000000-0000-0000-0000-000000000003")
                                                      "main" {})]
         (is (= :draft/add-error (:type result))))))))

(deftest add-unit-to-draft-returns-error-when-lord-added-to-reinforcements
  ((stub-add-unit [lord-unit] nil)
   (fn []
     (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-lord-eid "reinforcements" {})]
       (is (= :draft/add-error (:type result)))))))

(deftest add-unit-to-draft-returns-success-on-valid-add
  ((stub-add-unit [infantry-unit] nil)
   (fn []
     (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main" {})]
       (is (= :draft/add-success (:type result)))
       (is (contains? result :section))
       (is (contains? result :new-unit))
       (is (contains? result :budget))))))

(deftest add-unit-to-draft-includes-section-context-on-success
  ((stub-add-unit [infantry-unit] nil)
   (fn []
     (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main" {})]
       (is (= "main" (:section (:section result))))
       (is (= "main-army-section" (:section-id (:section result))))
       (is (= test-unit-eid (:eid (:new-unit result))))
       (is (= 100 (:section-cost (:budget result))))))))

(deftest add-unit-to-draft-succeeds-when-reinforcements-disabled
  ((stub-add-unit [infantry-unit] nil)
   (fn []
     (with-redefs [data-access.contract/get-game-mode-by-eid
                   (fn [_ _] (assoc test-game-mode :reinforcements-enabled 0))]
       (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main" {})]
         (is (= :draft/add-success (:type result))))))))

(deftest add-unit-to-draft-stores-mount-in-state
  (let [stored    (atom nil)
        mount-key "mount_barded_warhorse"
        mounts    [{:id   1                 :eid      (UUID/randomUUID) :key  mount-key
                    :name "Barded Warhorse" :icon-key mount-key         :cost 800}]]
    ((stub-add-unit [{:eid                test-unit-eid :name "Warrior Priest" :cost 100
                      :unit-category-name "Hero"
                      :unit-statistics    "{}"}]
                    nil
                    {:mounts mounts})
     (fn []
       (with-redefs [data-access.contract/upsert-draft-state (fn [_ _ json] (reset! stored json))]
         (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main"
                                           {:mount mount-key}))
       (let [state (when @stored
                     (jsonista.core/read-value @stored (jsonista.core/object-mapper {:decode-key-fn keyword})))]
         (is (= mount-key (get-in state [:main 0 :mount])))
         ;; set-draft-state runs Malli's string-transformer on encode, which
         ;; stringifies :int fields; compare the stored form directly.
         (is (= "900" (get-in state [:main 0 :total-cost]))))))))

;; --- remove-unit-from-draft ---

(def ^:private test-entry-eid (UUID/fromString "e0000000-0000-0000-0000-000000000001"))

(deftest remove-unit-from-draft-returns-success
  (let [existing-state (state-json {:main [[test-unit-eid test-entry-eid]] :reinforcements []})]
    (with-redefs [data-access.contract/get-draft-by-eid         (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid     (fn [_ _] test-game-mode)
                  data-access.contract/get-draft-state-by-draft (fn [_ _] {:state existing-state})
                  data-access.contract/upsert-draft-state       (fn [_ _ _] nil)
                  data-access.contract/get-units-for-faction    (fn [_ _] [infantry-unit])]
      (let [result (handlers.draft/remove-unit-from-draft test-deps test-draft-eid test-entry-eid "main")]
        (is (= :draft/remove-success (:type result)))
        (is (= test-entry-eid (:removed-entry-eid result)))
        (is (contains? result :removed-is-lord))
        (is (contains? result :budget))))))

(deftest remove-unit-from-draft-section-cost-is-zero-after-removal
  (let [existing-state (state-json {:main [[test-unit-eid test-entry-eid]] :reinforcements []})]
    (with-redefs [data-access.contract/get-draft-by-eid         (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid     (fn [_ _] test-game-mode)
                  data-access.contract/get-draft-state-by-draft (fn [_ _] {:state existing-state})
                  data-access.contract/upsert-draft-state       (fn [_ _ _] nil)
                  data-access.contract/get-units-for-faction    (fn [_ _] [infantry-unit])]
      (let [result (handlers.draft/remove-unit-from-draft test-deps test-draft-eid test-entry-eid "main")]
        (is (= 0 (:section-cost (:budget result))))))))

;; --- update-unit-in-draft ---

(deftest update-unit-in-draft-replaces-selections
  (let [existing-state (state-json {:main [[test-unit-eid test-entry-eid]] :reinforcements []})
        mount-key      "mount_warhorse"
        mounts         [{:id   1          :eid      (UUID/randomUUID) :key  mount-key
                         :name "Warhorse" :icon-key mount-key         :cost 50}]
        stored         (atom nil)]
    (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                  data-access.contract/get-draft-state-by-draft   (fn [_ _] {:state existing-state})
                  data-access.contract/upsert-draft-state         (fn [_ _ json] (reset! stored json))
                  data-access.contract/get-units-for-faction      (fn [_ _] [infantry-unit])
                  data-access.contract/get-unit-by-eid            (fn [_ _] infantry-unit)
                  data-access.contract/get-spells-by-keys         (fn [_ _] {})
                  data-access.contract/get-abilities-by-keys      (fn [_ _] {})
                  data-access.contract/get-items-for-unit         (fn [_ _] [])
                  data-access.contract/get-mounts-for-unit        (fn [_ _] mounts)
                  data-access.contract/get-unit-level-costs       (fn [_] test-level-costs)
                  data-access.contract/get-family-variants-by-eid (fn [_ _] [])]
      (let [result (handlers.draft/update-unit-in-draft test-deps test-draft-eid test-entry-eid "main"
                                                        {:mount mount-key})]
        (is (= :draft/update-success (:type result)))
        (is (= test-entry-eid (:entry-eid result)))
        (is (= 150 (:total-cost result)))
        (is (= 150 (:section-cost (:budget result))))
        (let [parsed (jsonista.core/read-value @stored (jsonista.core/object-mapper {:decode-key-fn keyword}))]
          (is (= (str test-entry-eid) (get-in parsed [:main 0 :entry-eid])))
          (is (= mount-key (get-in parsed [:main 0 :mount]))))))))

(deftest update-unit-in-draft-rejects-budget-violation
  (let [existing-state (state-json {:main [[test-unit-eid test-entry-eid]] :reinforcements []})
        mount-key      "mount_expensive"
        mounts         [{:id   1        :eid      (UUID/randomUUID) :key  mount-key
                         :name "Dragon" :icon-key mount-key         :cost 10000}]]
    (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                  data-access.contract/get-draft-state-by-draft   (fn [_ _] {:state existing-state})
                  data-access.contract/upsert-draft-state         (fn [_ _ _] nil)
                  data-access.contract/get-units-for-faction      (fn [_ _] [infantry-unit])
                  data-access.contract/get-unit-by-eid            (fn [_ _] infantry-unit)
                  data-access.contract/get-spells-by-keys         (fn [_ _] {})
                  data-access.contract/get-abilities-by-keys      (fn [_ _] {})
                  data-access.contract/get-items-for-unit         (fn [_ _] [])
                  data-access.contract/get-mounts-for-unit        (fn [_ _] mounts)
                  data-access.contract/get-unit-level-costs       (fn [_] test-level-costs)
                  data-access.contract/get-family-variants-by-eid (fn [_ _] [])]
      (let [result (handlers.draft/update-unit-in-draft test-deps test-draft-eid test-entry-eid "main"
                                                        {:mount mount-key})]
        (is (= :draft/update-error (:type result)))
        (is (string? (:message result)))))))

(deftest update-unit-in-draft-returns-error-when-entry-missing
  (with-redefs [data-access.contract/get-draft-by-eid         (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid     (fn [_ _] test-game-mode)
                data-access.contract/get-draft-state-by-draft (fn [_ _] nil)
                data-access.contract/get-units-for-faction    (fn [_ _] [infantry-unit])]
    (let [result (handlers.draft/update-unit-in-draft test-deps test-draft-eid test-entry-eid "main" {})]
      (is (= :draft/update-error (:type result))))))

(deftest update-unit-in-draft-at-per-unit-cap-is-allowed-same-unit
  ;; Four copies of the same unit already exist; editing one of them must not
  ;; fire the per-unit cap (the reduced army has only 3 copies when validating).
  (let [eeids          (mapv #(UUID/fromString (str "e0000000-0000-0000-0000-00000000000" %)) [1 2 3 4])
        existing-state (state-json {:main (mapv #(vector test-unit-eid %) eeids) :reinforcements []})]
    (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                  data-access.contract/get-draft-state-by-draft   (fn [_ _] {:state existing-state})
                  data-access.contract/upsert-draft-state         (fn [_ _ _] nil)
                  data-access.contract/get-units-for-faction      (fn [_ _] [infantry-unit])
                  data-access.contract/get-unit-by-eid            (fn [_ _] infantry-unit)
                  data-access.contract/get-spells-by-keys         (fn [_ _] {})
                  data-access.contract/get-abilities-by-keys      (fn [_ _] {})
                  data-access.contract/get-items-for-unit         (fn [_ _] [])
                  data-access.contract/get-mounts-for-unit        (fn [_ _] [])
                  data-access.contract/get-unit-level-costs       (fn [_] test-level-costs)
                  data-access.contract/get-family-variants-by-eid (fn [_ _] [])]
      (let [result (handlers.draft/update-unit-in-draft test-deps test-draft-eid (first eeids) "main" {})]
        (is (= :draft/update-success (:type result)))))))

(deftest remove-unit-from-draft-is-no-op-when-entry-not-in-section
  (with-redefs [data-access.contract/get-draft-by-eid         (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid     (fn [_ _] test-game-mode)
                data-access.contract/get-draft-state-by-draft (fn [_ _] nil)
                data-access.contract/upsert-draft-state       (fn [_ _ _] nil)
                data-access.contract/get-units-for-faction    (fn [_ _] [infantry-unit])]
    (let [result (handlers.draft/remove-unit-from-draft test-deps test-draft-eid test-entry-eid "main")]
      (is (= :draft/remove-success (:type result))))))

;; --- get-draft-unit-details ---

(deftest get-draft-unit-details-returns-draft-unit-type
  (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                data-access.contract/get-unit-by-eid            (fn [_ _] infantry-unit)
                data-access.contract/get-abilities-by-keys      (fn [_ _] {})
                data-access.contract/get-spells-by-keys         (fn [_ _] {})
                data-access.contract/get-items-for-unit         (fn [_ _] [])
                data-access.contract/get-mounts-for-unit        (fn [_ _] [])
                data-access.contract/get-family-variants-by-eid (fn [_ _] [])
                data-access.contract/get-lores-for-unit         (fn [_ _] [])]
    (let [result (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid)]
      (is (= :draft/unit (:type result))))))

(deftest get-draft-unit-details-attaches-draft-eid
  (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                data-access.contract/get-unit-by-eid            (fn [_ _] infantry-unit)
                data-access.contract/get-abilities-by-keys      (fn [_ _] {})
                data-access.contract/get-spells-by-keys         (fn [_ _] {})
                data-access.contract/get-items-for-unit         (fn [_ _] [])
                data-access.contract/get-mounts-for-unit        (fn [_ _] [])
                data-access.contract/get-family-variants-by-eid (fn [_ _] [])
                data-access.contract/get-lores-for-unit         (fn [_ _] [])]
    (let [result (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid)]
      (is (= test-draft-eid (:draft-eid result))))))

(deftest get-draft-unit-details-sets-can-add-to-reinforcements-from-game-mode
  (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                data-access.contract/get-unit-by-eid            (fn [_ _] infantry-unit)
                data-access.contract/get-abilities-by-keys      (fn [_ _] {})
                data-access.contract/get-spells-by-keys         (fn [_ _] {})
                data-access.contract/get-items-for-unit         (fn [_ _] [])
                data-access.contract/get-mounts-for-unit        (fn [_ _] [])
                data-access.contract/get-family-variants-by-eid (fn [_ _] [])
                data-access.contract/get-lores-for-unit         (fn [_ _] [])]
    (is (true? (get-in (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid)
                       [:validation :can-add-to-reinforcements?])))))

(deftest get-draft-unit-details-disables-reinforcements-when-game-mode-zero
  (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid       (fn [_ _] (assoc test-game-mode :reinforcements-enabled 0))
                data-access.contract/get-unit-by-eid            (fn [_ _] infantry-unit)
                data-access.contract/get-abilities-by-keys      (fn [_ _] {})
                data-access.contract/get-spells-by-keys         (fn [_ _] {})
                data-access.contract/get-items-for-unit         (fn [_ _] [])
                data-access.contract/get-mounts-for-unit        (fn [_ _] [])
                data-access.contract/get-family-variants-by-eid (fn [_ _] [])
                data-access.contract/get-lores-for-unit         (fn [_ _] [])]
    (is (false? (get-in (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid)
                        [:validation :can-add-to-reinforcements?])))))

;; --- mount overrides on embed-unit-for-entry ---

(def ^:private mount-stats-json
  "Mount override JSON: 800 cost, 6828 health, higher speed/charge."
  "{\"cost\":800,\"is_large\":true,\"unit_size\":1,\"health\":6828,\"barrier\":0,\"armor\":100,\"leadership\":85,\"speed\":92,\"melee_attack\":60,\"charge_bonus\":70,\"attributes\":[\"flying\",\"large\"]}")

(def ^:private mount-with-overrides
  {:id                   11
   :eid                  (UUID/fromString "11110000-0000-0000-0000-000000000001")
   :key                  "mount_deathclaw"
   :name                 "Deathclaw"
   :icon-key             "mount_deathclaw"
   :cost                 800
   :stats-override       mount-stats-json
   :granted-ability-keys "[\"ability_fiery_roar\"]"})

(def ^:private lord-with-mount-stats
  (assoc lord-unit
         :unit-statistics
         "{\"cost\":1100,\"is_large\":false,\"health\":4288,\"armor\":100,\"leadership\":80,\"speed\":34,\"melee_attack\":65,\"charge_bonus\":65,\"attributes\":[\"encourages\"]}"))

(def ^:private test-entry
  {:entry-eid (UUID/fromString "e0000000-0000-0000-0000-0000000000aa")
   :unit-eid  test-lord-eid
   :mount     "mount_deathclaw"
   :abilities []
   :spells    []
   :items     []})

(deftest get-draft-entry-details-falls-back-to-persisted-entry-when-no-overrides
  (with-redefs [handlers.draft/get-draft-entry (fn [_ _ _ _] test-entry)]
    (let [result (handlers.draft/get-draft-entry-details test-deps test-draft-eid (:entry-eid test-entry) "main")]
      (is (= "mount_deathclaw" (:mount result)))
      (is (= [] (:items result))))))

(deftest get-draft-entry-details-applies-selection-overrides-when-provided
  (with-redefs [handlers.draft/get-draft-entry (fn [_ _ _ _] test-entry)]
    (let [overrides {:mount "mount_barded_warhorse" :items ["wh_talisman_ss"] :spells [] :abilities []}
          result    (handlers.draft/get-draft-entry-details test-deps test-draft-eid (:entry-eid test-entry) "main" overrides)]
      (is (= "mount_barded_warhorse" (:mount result)))
      (is (= ["wh_talisman_ss"] (:items result))))))

(deftest get-draft-entry-details-nil-overrides-equivalent-to-no-overrides
  (with-redefs [handlers.draft/get-draft-entry (fn [_ _ _ _] test-entry)]
    (let [with    (handlers.draft/get-draft-entry-details test-deps test-draft-eid (:entry-eid test-entry) "main" nil)
          without (handlers.draft/get-draft-entry-details test-deps test-draft-eid (:entry-eid test-entry) "main")]
      (is (= with without)))))

(deftest hydrate-mount-overrides-parses-stats-override-into-draft-unit-stats
  (with-redefs [data-access.contract/get-abilities-by-keys (fn [_ _] {})]
    (let [hydrated (handlers.draft/hydrate-mount-overrides nil mount-with-overrides)]
      (is (= 6828 (:health-override hydrated)))
      (is (seq (:stats-override hydrated)))
      (is (every? (fn [s] (and (contains? s :stat) (contains? s :percentage))) (:stats-override hydrated))))))

(deftest hydrate-mount-overrides-exposes-attributes-override
  (with-redefs [data-access.contract/get-abilities-by-keys (fn [_ _] {})]
    (let [hydrated (handlers.draft/hydrate-mount-overrides nil mount-with-overrides)]
      (is (seq (:attributes-override hydrated)))
      (is (= #{"flying" "large"} (set (map :key (:attributes-override hydrated))))))))

(deftest hydrate-mount-overrides-resolves-granted-ability-keys-to-records
  (with-redefs [data-access.contract/get-abilities-by-keys
                (fn [_ _]
                  {"ability_fiery_roar" {:eid         (UUID/fromString "aaaa0000-0000-0000-0000-000000000001")
                                         :name        "Fiery Roar"
                                         :description "Roars menacingly."
                                         :cost        0}})]
    (let [hydrated (handlers.draft/hydrate-mount-overrides nil mount-with-overrides)
          [g]      (:granted-abilities hydrated)]
      (is (= "ability_fiery_roar" (:key g)))
      (is (= "Fiery Roar" (:name g))))))

(deftest hydrate-mount-overrides-leaves-base-fields-when-stats-override-missing
  (let [bare     (dissoc mount-with-overrides :stats-override :granted-ability-keys)
        hydrated (handlers.draft/hydrate-mount-overrides nil bare)]
    (is (nil? (:stats-override hydrated)))
    (is (nil? (:health-override hydrated)))
    (is (= [] (:granted-abilities hydrated)))))

(deftest embed-unit-for-entry-overlays-mount-health-when-mount-selected)
(with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
              data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
              data-access.contract/get-unit-by-eid            (fn [_ _] lord-with-mount-stats)
              data-access.contract/get-abilities-by-keys      (fn [_ _] {})
              data-access.contract/get-spells-by-keys         (fn [_ _] {})
              data-access.contract/get-items-for-unit         (fn [_ _] [])
              data-access.contract/get-mounts-for-unit        (fn [_ _] [mount-with-overrides])
              data-access.contract/get-unit-level-costs       (fn [_] test-level-costs)
              data-access.contract/get-family-variants-by-eid (fn [_ _] [])
              data-access.contract/get-lores-for-unit         (fn [_ _] [])])
(let [entry-resource {:eid       (:entry-eid test-entry)
                      :draft-eid test-draft-eid
                      :unit-eid  test-lord-eid
                      :section   "main"
                      :mount     "mount_deathclaw"
                      :abilities []
                      :spells    []
                      :items     []}
      result         (handlers.draft/embed-unit-for-entry test-deps entry-resource)
      unit           (get-in result [:_embedded :unit])]
  (is (= 6828 (:health unit)))
  (is (= 1000 (:total-cost unit)))
  (is (true? (:selected (first (:mounts unit))))))

(deftest get-draft-unit-details-excludes-mount-only-abilities-from-base-list
  ;; Karl-Franz-style fixture: unit_statistics lists Bloodroar in abilities,
  ;; and the Deathclaw mount grants Bloodroar. Bloodroar should be suppressed
  ;; from the unit's base draftable list — it only surfaces under
  ;; :mount-granted-abilities when Deathclaw is selected.
  (let [unit  (assoc lord-unit
                     :unit-statistics
                     "{\"abilities\":[\"hold_the_line\",\"bloodroar\",\"foe_seeker\"]}")
        mount {:id                   11
               :eid                  (UUID/fromString "11110000-0000-0000-0000-000000000002")
               :key                  "mount_deathclaw"
               :name                 "Deathclaw"
               :icon-key             "mount_deathclaw"
               :cost                 800
               :stats-override       nil
               :granted-ability-keys "[\"bloodroar\"]"}]
    (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                  data-access.contract/get-unit-by-eid            (fn [_ _] unit)
                  data-access.contract/get-abilities-by-keys      (fn [_ ks]
                                                                    (into {}
                                                                          (map (fn [k] [k {:eid  (UUID/randomUUID)
                                                                                           :name k
                                                                                           :cost (if (= k "bloodroar") 150 0)}]))
                                                                          ks))
                  data-access.contract/get-spells-by-keys         (fn [_ _] {})
                  data-access.contract/get-items-for-unit         (fn [_ _] [])
                  data-access.contract/get-mounts-for-unit        (fn [_ _] [mount])
                  data-access.contract/get-family-variants-by-eid (fn [_ _] [])
                  data-access.contract/get-lores-for-unit         (fn [_ _] [])]
      (let [result             (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid)
            draftable-keys     (set (map :key (:draftable-abilities result)))
            passive-keys       (set (map :key (:passive-abilities result)))
            mount-granted-keys (set (map :key (:granted-abilities (first (:mounts result)))))]
        (is (not (contains? draftable-keys "bloodroar")))
        (is (not (contains? passive-keys "bloodroar")))
        (is (contains? mount-granted-keys "bloodroar"))
        (is (= #{"hold_the_line" "foe_seeker"}
               (into draftable-keys passive-keys)))))))

(deftest embed-unit-for-entry-leaves-base-stats-when-no-mount-selected)
(with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
              data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
              data-access.contract/get-unit-by-eid            (fn [_ _] lord-with-mount-stats)
              data-access.contract/get-abilities-by-keys      (fn [_ _] {})
              data-access.contract/get-spells-by-keys         (fn [_ _] {})
              data-access.contract/get-items-for-unit         (fn [_ _] [])
              data-access.contract/get-mounts-for-unit        (fn [_ _] [mount-with-overrides])
              data-access.contract/get-unit-level-costs       (fn [_] test-level-costs)
              data-access.contract/get-family-variants-by-eid (fn [_ _] [])
              data-access.contract/get-lores-for-unit         (fn [_ _] [])])
(let [entry-resource {:eid       (:entry-eid test-entry)
                      :draft-eid test-draft-eid
                      :unit-eid  test-lord-eid
                      :section   "main"
                      :mount     nil
                      :abilities []
                      :spells    []
                      :items     []}
      result         (handlers.draft/embed-unit-for-entry test-deps entry-resource)
      unit           (get-in result [:_embedded :unit])]
  (is (= 4288 (:health unit)))
  (is (= 200 (:total-cost unit)))
  (is (= [] (:mount-granted-abilities unit))))

;; --- lore overrides ----------------------------------------------------------

(def ^:private fire-lore
  {:id           94
   :eid          (UUID/fromString "f100005e-0000-0000-0000-000000000000")
   :key          "wh_main_lore_fire"
   :name         "Lore of Fire"
   :cost         0
   :portrait-key "000a0006-0000-4000-8000-000000000000"})

(def ^:private life-lore
  {:id           84
   :eid          (UUID/fromString "f1000054-0000-0000-0000-000000000000")
   :key          "wh_dlc05_lore_life"
   :name         "Lore of Life"
   :cost         0
   :portrait-key "000a0009-0000-4000-8000-000000000000"})

;; Stub the canonical lore→spells lookup used by apply-lore-overrides.
;; Each lore returns two fake spells with positive cost so they end up
;; classified as :draftable-spells.
(def ^:private lore-spell-map
  {"wh_main_lore_fire"  ["fire_spell_a" "fire_spell_b"]
   "wh_dlc05_lore_life" ["life_spell_a" "life_spell_b"]})

(defn- stub-lore-spells
  [_ lore-key]
  (mapv (fn [k] {:key k :eid (UUID/randomUUID) :name k :mana-cost 8 :cost 60})
        (get lore-spell-map lore-key [])))

(def ^:private lore-unit
  {:eid                (UUID/fromString "000a0004-0000-4000-8000-000000000000")
   :name               "Archmage"
   :cost               450
   :unit-category-name "Lord"
   :unit-statistics    "{\"cost\":450,\"health\":3820,\"draftable-spells\":[]}"})

(defn- stub-spell-lookup
  "Returns {key → spell record} for any key — cost 60, mana 8 — so the
  resolved :draftable-spells are non-empty."
  [_ keys]
  (into {} (map (fn [k] [k {:eid       (UUID/randomUUID)
                            :name      k
                            :mana-cost 8
                            :cost      60}]))
        keys))

(deftest apply-lore-overrides-without-lore-leaves-base-fields
  (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                data-access.contract/get-unit-by-eid            (fn [_ _] lore-unit)
                data-access.contract/get-abilities-by-keys      (fn [_ _] {})
                data-access.contract/get-spells-by-keys         stub-spell-lookup
                data-access.contract/get-items-for-unit         (fn [_ _] [])
                data-access.contract/get-mounts-for-unit        (fn [_ _] [])
                data-access.contract/get-family-variants-by-eid (fn [_ _] [])
                data-access.contract/get-lores-for-unit         (fn [_ _] [fire-lore life-lore])
                data-access.contract/get-spells-for-lore        stub-lore-spells]
    (let [unit (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid)]
      (is (= [] (:draftable-spells unit)))
      (is (nil? (:lore-portrait-key unit)))
      (is (= 2 (count (:lores unit))))
      (is (every? (complement :selected) (:lores unit))))))

(deftest apply-lore-overrides-with-selected-lore-swaps-spells-and-portrait)
(with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
              data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
              data-access.contract/get-unit-by-eid            (fn [_ _] lore-unit)
              data-access.contract/get-abilities-by-keys      (fn [_ _] {})
              data-access.contract/get-spells-by-keys         stub-spell-lookup
              data-access.contract/get-items-for-unit         (fn [_ _] [])
              data-access.contract/get-mounts-for-unit        (fn [_ _] [])
              data-access.contract/get-unit-level-costs       (fn [_] test-level-costs)
              data-access.contract/get-family-variants-by-eid (fn [_ _] [])
              data-access.contract/get-lores-for-unit         (fn [_ _] [fire-lore life-lore])
              data-access.contract/get-spells-for-lore        stub-lore-spells])
(let [result (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid
                                                    {:mount     nil
                                                     :lore      "wh_main_lore_fire"
                                                     :abilities []
                                                     :spells    []
                                                     :items     []})
      spells (:draftable-spells result)]
  (is (= "wh_main_lore_fire" (:lore result)))
  (is (= "000a0006-0000-4000-8000-000000000000" (:lore-portrait-key result)))
  (is (= #{"fire_spell_a" "fire_spell_b"} (set (map :key spells))))
  (is (true? (:selected (first (filter #(= "wh_main_lore_fire" (:key %)) (:lores result))))))
  (is (false? (:selected (first (filter #(= "wh_dlc05_lore_life" (:key %)) (:lores result)))))))

(deftest update-unit-in-draft-clears-spells-when-lore-changes
  (let [entry-eid (UUID/fromString "e0000000-0000-0000-0000-0000000000bb")
        existing  {:entry-eid entry-eid
                   :unit-eid  (:eid lore-unit)
                   :mount     nil
                   :lore      "wh_main_lore_fire"
                   :abilities []
                   :spells    ["fire_spell_a"]
                   :items     []}
        state-map {:main [existing] :reinforcements []}
        captured  (atom nil)]
    (with-redefs [data-access.contract/get-draft-by-eid           (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid       (fn [_ _] test-game-mode)
                  data-access.contract/get-draft-state-by-draft   (fn [_ _] {:state (jsonista.core/write-value-as-string state-map)})
                  data-access.contract/upsert-draft-state         (fn [_ _ json] (reset! captured (jsonista.core/read-value json (jsonista.core/object-mapper {:decode-key-fn keyword}))))
                  data-access.contract/get-units-for-faction      (fn [_ _] [lore-unit])
                  data-access.contract/get-unit-by-eid            (fn [_ _] lore-unit)
                  data-access.contract/get-spells-by-keys         stub-spell-lookup
                  data-access.contract/get-abilities-by-keys      (fn [_ _] {})
                  data-access.contract/get-items-for-unit         (fn [_ _] [])
                  data-access.contract/get-mounts-for-unit        (fn [_ _] [])
                  data-access.contract/get-unit-level-costs       (fn [_] test-level-costs)
                  data-access.contract/get-family-variants-by-eid (fn [_ _] [])
                  data-access.contract/get-lores-for-unit         (fn [_ _] [fire-lore life-lore])
                  data-access.contract/get-spells-for-lore        stub-lore-spells]
      (let [result    (handlers.draft/update-unit-in-draft test-deps test-draft-eid entry-eid "main"
                                                           {:mount     nil
                                                            :lore      "wh_dlc05_lore_life"
                                                            :abilities []
                                                            ;; Client re-sends the old Fire spell — must be dropped.
                                                            :spells    ["fire_spell_a"]
                                                            :items     []})
            new-entry (-> @captured :main first)]
        (is (= :draft/update-success (:type result)))
        (is (= "wh_dlc05_lore_life" (:lore new-entry)))
        (is (= [] (:spells new-entry)))))))

;; --- veteran level cost ---

(deftest apply-level-cost-rank-zero-is-noop
  (is (= 800 (handlers.draft/apply-level-cost 800 (get test-level-costs 0)))))

(deftest apply-level-cost-rank-one-rounds-and-adds-fixed
  ;; round(800 * 1.03) + 11 = 824 + 11 = 835
  (is (= 835 (handlers.draft/apply-level-cost 800 (get test-level-costs 1)))))

(deftest apply-level-cost-falls-back-to-base-when-row-missing
  ;; preserves base when the lookup is empty (e.g. data source unavailable)
  (is (= 800 (handlers.draft/apply-level-cost 800 nil))))

(deftest add-unit-to-draft-applies-level-cost-adjustment
  ;; base 100 + level 1 (mult 1.03, fixed 11) = round(103) + 11 = 114
  ((stub-add-unit [infantry-unit] nil)
   (fn []
     (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main"
                                                    {:level 1})]
       (is (= :draft/add-success (:type result)))
       (is (= 114 (:total-cost (:new-unit result))))
       (is (= 1 (:level (:new-unit result))))))))

(deftest add-unit-to-draft-persists-level-in-state
  (let [stored (atom nil)]
    ((stub-add-unit [infantry-unit] nil)
     (fn []
       (with-redefs [data-access.contract/upsert-draft-state (fn [_ _ json] (reset! stored json))]
         (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main"
                                           {:level 1}))
       (let [parsed (jsonista.core/read-value @stored (jsonista.core/object-mapper {:decode-key-fn keyword}))]
         ;; Malli encode runs the string-transformer on :int fields, so :level
         ;; round-trips through JSON as a string.
         (is (= "1" (get-in parsed [:main 0 :level]))))))))
