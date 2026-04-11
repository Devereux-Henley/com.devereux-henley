(ns com.devereux-henley.rts-domain.handlers.draft-test
  (:require
   [clojure.test :refer [deftest is]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.draft :as handlers.draft]
   [jsonista.core])
  (:import
   [java.time Instant]
   [java.util UUID]))

(defn- state-json
  "Builds a draft-state JSON string from maps of {:main [uuid …] :reinforcements [uuid …]}."
  [{:keys [main reinforcements]}]
  (letfn [(entry [uid] (str "{\"unit-eid\":\"" uid "\",\"mount\":null,\"spells\":[],\"items\":[],\"total-cost\":null}"))]
    (str "{\"main\":["  (clojure.string/join "," (map entry main))
         "],\"reinforcements\":[" (clojure.string/join "," (map entry reinforcements)) "]}")))

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
  {:eid                  test-game-mode-eid
   :draft-value          1000
   :reinforcement-value  500
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

(deftest parse-unit-statistics-returns-empty-on-invalid-json
  (let [result (handlers.draft/parse-unit-statistics "not-json")]
    (is (= [] (:stats result)))
    (is (= [] (:abilities result)))))

(deftest parse-unit-statistics-returns-empty-on-nil
  (let [result (handlers.draft/parse-unit-statistics nil)]
    (is (= [] (:stats result)))
    (is (= [] (:abilities result)))))

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

(defn- stub-add-unit
  "Returns a fixture fn for add-unit-to-draft tests.
   faction-units  — list returned by get-units-for-faction.
   existing-state-json — :state string for get-draft-state-by-draft, or nil."
  [faction-units existing-state-json]
  (fn [f]
    (with-redefs [data-access.contract/get-draft-by-eid         (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid     (fn [_ _] test-game-mode)
                  data-access.contract/get-draft-state-by-draft (fn [_ _] (when existing-state-json {:state existing-state-json}))
                  data-access.contract/get-units-for-faction    (fn [_ _] faction-units)
                  data-access.contract/get-spells-by-keys       (fn [_ _] {})
                  data-access.contract/upsert-draft-state       (fn [_ _ _] nil)]
      (f))))

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
                     {:eid (UUID/fromString "c0000000-0000-0000-0000-000000000003")
                      :name "Archaon"
                      :cost 200
                      :unit-category-name "Lord"
                      :unit-statistics "{}"}]
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
       (is (contains? result :main-section))
       (is (contains? result :reinf-section))))))

(deftest add-unit-to-draft-includes-section-context-on-success
  ((stub-add-unit [infantry-unit] nil)
   (fn []
     (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main" {})]
       (is (= "main" (:section (:main-section result))))
       (is (= 100 (:section-cost (:main-section result))))))))

(deftest add-unit-to-draft-omits-reinf-section-when-disabled
  ((stub-add-unit [infantry-unit] nil)
   (fn []
     (with-redefs [data-access.contract/get-game-mode-by-eid
                   (fn [_ _] (assoc test-game-mode :reinforcements-enabled 0))]
       (let [result (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main" {})]
         (is (= :draft/add-success (:type result)))
         (is (nil? (:reinf-section result))))))))

(deftest add-unit-to-draft-stores-mount-in-state
  (let [stored (atom nil)]
    ((stub-add-unit [{:eid test-unit-eid :name "Warrior Priest" :cost 100
                      :unit-category-name "Hero"
                      :unit-statistics "{\"mounts\":[{\"name\":\"Barded Warhorse\",\"mp_cost\":800}]}"}]
                    nil)
     (fn []
       (with-redefs [data-access.contract/upsert-draft-state (fn [_ _ json] (reset! stored json))]
         (handlers.draft/add-unit-to-draft test-deps test-draft-eid test-unit-eid "main"
                                           {:mount "Barded Warhorse"}))
       (let [state (when @stored
                     (jsonista.core/read-value @stored (jsonista.core/object-mapper {:decode-key-fn keyword})))]
         (is (= "Barded Warhorse" (get-in state [:main 0 :mount])))
         (is (= 900 (get-in state [:main 0 :total-cost]))))))))

;; --- remove-unit-from-draft ---

(deftest remove-unit-from-draft-returns-success
  (let [existing-state (state-json {:main [test-unit-eid] :reinforcements []})]
    (with-redefs [data-access.contract/get-draft-by-eid         (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid     (fn [_ _] test-game-mode)
                  data-access.contract/get-draft-state-by-draft (fn [_ _] {:state existing-state})
                  data-access.contract/upsert-draft-state       (fn [_ _ _] nil)
                  data-access.contract/get-units-for-faction    (fn [_ _] [infantry-unit])]
      (let [result (handlers.draft/remove-unit-from-draft test-deps test-draft-eid test-unit-eid "main")]
        (is (= :draft/remove-success (:type result)))
        (is (contains? result :main-section))
        (is (contains? result :reinf-section))))))

(deftest remove-unit-from-draft-section-cost-is-zero-after-removal
  (let [existing-state (state-json {:main [test-unit-eid] :reinforcements []})]
    (with-redefs [data-access.contract/get-draft-by-eid         (fn [_ _] test-draft)
                  data-access.contract/get-game-mode-by-eid     (fn [_ _] test-game-mode)
                  data-access.contract/get-draft-state-by-draft (fn [_ _] {:state existing-state})
                  data-access.contract/upsert-draft-state       (fn [_ _ _] nil)
                  data-access.contract/get-units-for-faction    (fn [_ _] [infantry-unit])]
      (let [result (handlers.draft/remove-unit-from-draft test-deps test-draft-eid test-unit-eid "main")]
        (is (= 0 (:section-cost (:main-section result))))))))

(deftest remove-unit-from-draft-is-no-op-when-unit-not-in-section
  (with-redefs [data-access.contract/get-draft-by-eid         (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid     (fn [_ _] test-game-mode)
                data-access.contract/get-draft-state-by-draft (fn [_ _] nil)
                data-access.contract/upsert-draft-state       (fn [_ _ _] nil)
                data-access.contract/get-units-for-faction    (fn [_ _] [infantry-unit])]
    (let [result (handlers.draft/remove-unit-from-draft test-deps test-draft-eid test-unit-eid "main")]
      (is (= :draft/remove-success (:type result))))))

;; --- get-draft-unit-details ---

(deftest get-draft-unit-details-returns-draft-unit-type
  (with-redefs [data-access.contract/get-draft-by-eid     (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid (fn [_ _] test-game-mode)
                data-access.contract/get-unit-by-eid      (fn [_ _] infantry-unit)
                data-access.contract/get-abilities-by-names (fn [_ _] {})]
    (let [result (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid)]
      (is (= :draft/unit (:type result))))))

(deftest get-draft-unit-details-attaches-draft-eid
  (with-redefs [data-access.contract/get-draft-by-eid      (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid  (fn [_ _] test-game-mode)
                data-access.contract/get-unit-by-eid       (fn [_ _] infantry-unit)
                data-access.contract/get-abilities-by-names (fn [_ _] {})]
    (let [result (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid)]
      (is (= test-draft-eid (:draft-eid result))))))

(deftest get-draft-unit-details-sets-reinforcements-enabled-from-game-mode
  (with-redefs [data-access.contract/get-draft-by-eid      (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid  (fn [_ _] test-game-mode)
                data-access.contract/get-unit-by-eid       (fn [_ _] infantry-unit)
                data-access.contract/get-abilities-by-names (fn [_ _] {})]
    (is (true? (:reinforcements-enabled (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid))))))

(deftest get-draft-unit-details-sets-reinforcements-disabled-when-zero
  (with-redefs [data-access.contract/get-draft-by-eid      (fn [_ _] test-draft)
                data-access.contract/get-game-mode-by-eid  (fn [_ _] (assoc test-game-mode :reinforcements-enabled 0))
                data-access.contract/get-unit-by-eid       (fn [_ _] infantry-unit)
                data-access.contract/get-abilities-by-names (fn [_ _] {})]
    (is (false? (:reinforcements-enabled (handlers.draft/get-draft-unit-details test-deps test-draft-eid test-unit-eid))))))
