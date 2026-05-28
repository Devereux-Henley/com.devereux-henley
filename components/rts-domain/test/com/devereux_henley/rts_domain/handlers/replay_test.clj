(ns com.devereux-henley.rts-domain.handlers.replay-test
  (:require
   [clojure.java.shell :as shell]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.replay :as handlers.replay]
   [jsonista.core :as jsonista])
  (:import
   [java.util UUID]))

;; The submit flow auto-creates one draft per side per game from the
;; parsed replay. The handful of new data-access fns it pulls in are
;; defaulted here to safe no-ops so the validation-focused tests don't
;; need to know about them; the persistence-flow tests below override
;; the relevant stubs to assert draft side-effects.
;;
;; When a match clinches, record-game-from-parsed delegates to
;; handlers.tournament/update-match-result so standings recalculate —
;; which pulls in the tournament-state + all-matches stubs below;
;; defaulted to a minimal empty state with no phases so
;; recalculate-and-check-completion is a no-op for these tests.
(use-fixtures :each
  (fn [t]
    (with-redefs [data-access.contract/get-tournament-by-eid      (fn [_ _] {:name "Practice" :game-eid (UUID/randomUUID)})
                  data-access.contract/get-game-modes-for-game    (fn [_ _] [{:eid (UUID/randomUUID) :name "Land Battle"}])
                  data-access.contract/get-subfactions-by-keys    (fn [_ _] [])
                  data-access.contract/get-units-by-keys          (fn [_ _] [])
                  data-access.contract/get-mounts-for-unit        (fn [_ _] [])
                  data-access.contract/get-unit-level-costs       (fn [_] {})
                  data-access.contract/create-draft!              (fn [_ spec] (assoc spec :version 1))
                  data-access.contract/add-entry!                 (fn [_ _ _] nil)
                  data-access.contract/get-tournament-state       (fn [_ _] nil)
                  data-access.contract/upsert-tournament-state    (fn [_ _ _] nil)
                  data-access.contract/get-matches-for-tournament (fn [_ _] [])]
      (t))))

(def ^:private match-eid (UUID/fromString "00000000-0000-4000-8000-000000000001"))
(def ^:private deps      {:connection nil :datalog-connection nil :replay-parser-bin "/fake/tw-replay-parser"})

(def ^:private bo1-match
  {:eid            match-eid
   :tournament-eid (UUID/randomUUID)
   :phase-index    0
   :round-index    0
   :status         "pending"
   :format         1
   :player-one-sub "sigmar_42"
   :player-two-sub "runemaster"})

(def ^:private sample-parser-output
  "Mirrors what the Rust binary emits — snake_case keys. Used by the
  parse-replay-file tests that exercise the JSON → kebab conversion."
  {:schema_version                1
   :format                        "CBAB"
   :match_id                      "7801776992105"
   :played_at                     {:year 2026 :month 4 :day 24 :hour 3 :minute 55 :second 5}
   :victory_condition             "BATTLE_SETUP_VICTORY_CONDITION_CAPTURE_LOCATION_SCORE"
   :uploader_local_alliance_index 0
   :alliances                     [{:index 0 :faction_key "wh_main_emp_empire" :model_count 1957 :armies []}
                                   {:index 1 :faction_key "wh3_dlc23_chd_legion_of_azgorh" :model_count 1816 :armies []}]})

(def ^:private sample-parsed-game
  "Already-kebab-converted parsed map (what record-game-from-parsed sees
  after parse-replay-file has run)."
  {:schema-version                1
   :format                        "CBAB"
   :match-id                      "7801776992105"
   :played-at                     {:year 2026 :month 4 :day 24 :hour 3 :minute 55 :second 5}
   :victory-condition             "BATTLE_SETUP_VICTORY_CONDITION_CAPTURE_LOCATION_SCORE"
   :uploader-local-alliance-index 0
   :alliances                     [{:index 0 :faction-key "wh_main_emp_empire" :model-count 1957 :armies []}
                                   {:index 1 :faction-key "wh3_dlc23_chd_legion_of_azgorh" :model-count 1816 :armies []}]})

(defn- mock-shell-success [parsed]
  (fn [& _] {:exit 0 :out (jsonista/write-value-as-string parsed) :err ""}))

(defn- mock-shell-failure [exit-code stderr]
  (fn [& _] {:exit exit-code :out "" :err stderr}))

;; ─── parse-replay-file ─────────────────────────────────────────────────────

(deftest parse-replay-file-converts-snake-to-kebab
  (with-redefs [shell/sh (mock-shell-success sample-parser-output)]
    (let [result (handlers.replay/parse-replay-file deps "/tmp/x.replay")]
      (is (= "7801776992105" (:match-id result)))
      (is (= "CBAB" (:format result)))
      (is (= 0 (:uploader-local-alliance-index result)))
      (is (= "wh_main_emp_empire" (get-in result [:alliances 0 :faction-key]))))))

(deftest parse-replay-file-throws-on-non-zero-exit
  (with-redefs [shell/sh (mock-shell-failure 1 "decode failed")]
    (let [thrown (try (handlers.replay/parse-replay-file deps "/tmp/x.replay") nil
                      (catch Exception e e))]
      (is (some? thrown))
      (is (= :error/invalid (:error/kind (ex-data thrown))))
      (is (= "decode failed" (:stderr (ex-data thrown)))))))

(deftest parse-replay-file-honours-bin-dependency
  (let [captured (atom nil)]
    (with-redefs [shell/sh (fn [& args] (reset! captured args) {:exit 0 :out "{}" :err ""})]
      (handlers.replay/parse-replay-file {:replay-parser-bin "/opt/x"} "/tmp/y.replay")
      (is (= ["/opt/x" "/tmp/y.replay"] @captured)))))

;; ─── record-game-from-parsed ───────────────────────────────────────────────

(defn- valid-submission [winner-sub]
  {:parsed          sample-parsed-game
   :winner-sub      winner-sub
   :source-name     "g.replay"
   :uploaded-by-sub "sigmar_42"})

(deftest record-game-rejects-missing-match
  (with-redefs [data-access.contract/get-match-by-eid (fn [_ _] nil)]
    (let [r (handlers.replay/record-game-from-parsed deps match-eid (valid-submission "sigmar_42"))]
      (is (= :match-record/error (:type r)))
      (is (= "Match not found." (:message r))))))

(deftest record-game-rejects-already-complete
  (with-redefs [data-access.contract/get-match-by-eid (fn [_ _] (assoc bo1-match :status "complete"))]
    (let [r (handlers.replay/record-game-from-parsed deps match-eid (valid-submission "sigmar_42"))]
      (is (= :match-record/error (:type r)))
      (is (= "Match is already complete." (:message r))))))

(deftest record-game-rejects-unknown-winner-sub
  (with-redefs [data-access.contract/get-match-by-eid    (fn [_ _] bo1-match)
                data-access.contract/get-games-for-match (fn [_ _] [])]
    (let [r (handlers.replay/record-game-from-parsed deps match-eid (valid-submission "stranger"))]
      (is (= :match-record/error (:type r)))
      (is (re-find #"one of the match's players" (:message r))))))

(deftest record-game-rejects-when-series-full
  (with-redefs [data-access.contract/get-match-by-eid    (fn [_ _] bo1-match)
                data-access.contract/get-games-for-match (fn [_ _] [{:winner-sub "sigmar_42"}])]
    (let [r (handlers.replay/record-game-from-parsed deps match-eid (valid-submission "sigmar_42"))]
      (is (= :match-record/error (:type r)))
      (is (re-find #"maximum" (:message r))))))

(deftest record-game-resolves-mount-from-parsed-key-suffix
  ;; Regression: the mount-needing filter previously checked against the
  ;; resolved-rows-map keys, but the mount-suffixed parser key
  ;; (e.g. `..._sorcerer_prophet_fire_great_taurus`) never lives there —
  ;; only its un-mounted prefix does. The filter must consult the
  ;; engine-emitted keys directly so `get-mounts-for-unit` is hit for
  ;; rows that have a mounted variant in the parsed game.
  (let [stored-states     (atom [])
        unit-eid          (UUID/randomUUID)
        faction-eid       (UUID/randomUUID)
        base-key          "wh3_dlc23_chd_cha_sorcerer_prophet_fire"
        mounted-key       (str base-key "_great_taurus")
        parsed-with-mount {:schema-version                1
                           :format                        "CBAB"
                           :match-id                      "mount-test"
                           :played-at                     {:year 2026 :month 4 :day 24 :hour 3 :minute 55 :second 5}
                           :victory-condition             "BATTLE_SETUP_VICTORY_CONDITION_CAPTURE_LOCATION_SCORE"
                           :uploader-local-alliance-index 0
                           :alliances                     [{:index 0 :faction-key "wh_main_emp_empire" :model-count 1957 :armies []}
                                                           {:index       1
                                                            :faction-key "wh3_dlc23_chd_legion_of_azgorh"
                                                            :model-count 1816
                                                            :armies      [{:index            0
                                                                           :is-reinforcement false
                                                                           :units            [{:cost          900
                                                                                               :adjusted-cost 2962
                                                                                               :key           mounted-key
                                                                                               :level         0
                                                                                               :spells        []}]}]}]}]
    (with-redefs [data-access.contract/get-match-by-eid        (fn [_ _] bo1-match)
                  data-access.contract/get-games-for-match     (fn [_ _] [])
                  data-access.contract/create-replay           (fn [_ spec] (assoc spec :id 1))
                  data-access.contract/create-game             (fn [_ _ gi w _] {:game-index gi :winner-sub w})
                  data-access.contract/update-match-result     (fn [_ _ _] nil)
                  data-access.contract/get-tournament-by-eid   (fn [_ _] {:name "Mount Cup" :game-eid (UUID/randomUUID)})
                  data-access.contract/get-game-modes-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Land Battle"}])
                  data-access.contract/get-subfactions-by-keys (fn [_ _] [{:key "wh3_dlc23_chd_legion_of_azgorh" :faction-eid faction-eid}])
                  data-access.contract/get-units-by-keys       (fn [_ _] [{:eid unit-eid :key base-key :cost 900}])
                  data-access.contract/get-mounts-for-unit     (fn [_ eid]
                                                                 (when (= eid unit-eid)
                                                                   [{:key "mount_great_taurus" :name "Great Taurus" :cost 300}
                                                                    {:key "mount_lammasu" :name "Lammasu" :cost 200}]))
                  data-access.contract/create-draft!           (fn [_ spec] (assoc spec :version 1))
                  data-access.contract/add-entry!              (fn [_ draft-eid entry]
                                                                 (swap! stored-states conj (assoc entry :draft-eid draft-eid)))]
      (handlers.replay/record-game-from-parsed
       deps match-eid
       {:parsed          parsed-with-mount
        :winner-sub      "sigmar_42"
        :source-name     "g.replay"
        :uploaded-by-sub "sigmar_42"})
      ;; A `:main` entry whose unit-eid matches the Chaos Dwarfs sorcerer
      ;; row must have been transacted with the mount, total, and engine
      ;; costs the parser+domain agreed on.
      (let [main-entry (some (fn [e]
                               (when (and (= :main (:section e))
                                          (= unit-eid (:unit-eid e)))
                                 e))
                             @stored-states)]
        (is (some? main-entry) "main-section entry for the resolved unit was transacted")
        (is (= "mount_great_taurus" (:mount main-entry))
            "mount suffix on the parsed key picks the matching mount row")
        (is (= 1200 (:total-cost main-entry))
            ":total-cost is recomputed (base 900 + mount 300) — slot card and panel agree by construction")
        (is (= 2962 (:engine-cost main-entry))
            ":engine-cost preserves the parser-emitted true cost for audit")))))

(deftest record-game-persists-and-completes-bo1
  (let [stored-replays  (atom [])
        stored-games    (atom [])
        stored-drafts   (atom [])
        match-completed (atom nil)
        emp-faction-eid (UUID/randomUUID)
        chd-faction-eid (UUID/randomUUID)]
    (with-redefs [data-access.contract/get-match-by-eid        (fn [_ _] bo1-match)
                  data-access.contract/get-games-for-match     (fn [_ _] [])
                  data-access.contract/create-replay           (fn [_ spec]
                                                                 (swap! stored-replays conj spec)
                                                                 spec)
                  data-access.contract/create-game             (fn [_ _meid game-index winner-sub opts]
                                                                 (let [g (merge {:game-index game-index
                                                                                 :winner-sub winner-sub
                                                                                 :replay-eid (:replay-eid opts)}
                                                                                (select-keys opts
                                                                                             [:player-one-draft-eid
                                                                                              :player-two-draft-eid]))]
                                                                   (swap! stored-games conj g)
                                                                   g))
                  data-access.contract/update-match-result     (fn [_ _ winner] (reset! match-completed winner))
                  data-access.contract/get-tournament-by-eid   (fn [_ _] {:name "Spring Open" :game-eid (UUID/randomUUID)})
                  data-access.contract/get-game-modes-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Land Battle"}])
                  data-access.contract/get-subfactions-by-keys (fn [_ keys]
                                                                 (let [k (first keys)]
                                                                   (cond
                                                                     (= k "wh_main_emp_empire")
                                                                     [{:key k :faction-eid emp-faction-eid}]
                                                                     (= k "wh3_dlc23_chd_legion_of_azgorh")
                                                                     [{:key k :faction-eid chd-faction-eid}]
                                                                     :else [])))
                  data-access.contract/create-draft!           (fn [_ spec]
                                                                 (swap! stored-drafts conj spec)
                                                                 (assoc spec :version 1))
                  data-access.contract/add-entry!              (fn [_ _ _] nil)]
      (let [result (handlers.replay/record-game-from-parsed
                    deps match-eid (valid-submission "sigmar_42"))]
        (testing "one replay row persisted"
          (is (= 1 (count @stored-replays)))
          (is (= "sigmar_42" (-> @stored-replays first :uploaded-by-sub))))
        (testing "one match_game row persisted"
          (is (= 1 (count @stored-games)))
          (is (uuid? (-> @stored-games first :replay-eid))))
        (testing "Bo1 winner crowned immediately"
          (is (= "sigmar_42" @match-completed))
          (is (= "sigmar_42" (:winner-sub result)))
          (is (true? (:match-complete? result))))
        (testing "auto-creates one draft per side"
          (is (= 2 (count @stored-drafts))
              "Bo1 with 1 game → 2 drafts (one per side)")
          (is (= #{"sigmar_42" "runemaster"}
                 (set (map :player-sub @stored-drafts)))
              "drafts split between match.player_one_sub / player_two_sub")
          (is (= {emp-faction-eid 1 chd-faction-eid 1}
                 (frequencies (map :faction-eid @stored-drafts)))
              "factions resolved from parsed faction_key per alliance"))))))
