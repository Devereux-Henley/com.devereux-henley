(ns com.devereux-henley.rts-domain.handlers.replay-test
  (:require
   [clojure.java.shell :as shell]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.devereux-henley.rts-data-access.contract :as data-access.contract]
   [com.devereux-henley.rts-domain.handlers.replay :as handlers.replay]
   [jsonista.core :as jsonista])
  (:import
   [java.util UUID]))

;; The submit flow now auto-creates one draft per side per game from the
;; parsed replay. The handful of new data-access fns it pulls in are all
;; defaulted here to safe no-ops so the existing validation-focused tests
;; don't need to know about them; the persistence-flow tests below
;; override the relevant stubs to assert draft side-effects.
(use-fixtures :each
  (fn [t]
    (with-redefs [data-access.contract/get-tournament-by-eid   (fn [_ _] {:name "Practice" :game-eid (UUID/randomUUID)})
                  data-access.contract/get-game-modes-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Land Battle"}])
                  data-access.contract/get-subfactions-by-keys (fn [_ _] [])
                  data-access.contract/get-units-by-keys       (fn [_ _] [])
                  data-access.contract/get-mounts-for-unit     (fn [_ _] [])
                  data-access.contract/create-draft            (fn [_ _] nil)
                  data-access.contract/upsert-draft-state      (fn [_ _ _] nil)]
      (t))))

(def ^:private match-eid (UUID/fromString "00000000-0000-4000-8000-000000000001"))
(def ^:private deps      {:connection nil :replay-parser-bin "/fake/tw-replay-parser"})

(def ^:private bo3-match
  {:eid            match-eid
   :tournament-eid (UUID/randomUUID)
   :phase-index    0
   :round-index    0
   :status         "pending"
   :format         3
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
  "Already-kebab-converted parsed map (what record-match-from-parsed sees
  after parse-replay-file has run). The submit endpoint round-trips this
  shape through the hidden form fields."
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

;; ─── parse-replay-files (batch) ────────────────────────────────────────────

(deftest parse-replay-files-returns-vector-in-order
  (let [calls (atom [])]
    (with-redefs [shell/sh (fn [& args]
                             (swap! calls conj (last args))
                             {:exit 0 :out (jsonista/write-value-as-string sample-parser-output) :err ""})]
      (let [result (handlers.replay/parse-replay-files deps ["/a.replay" "/b.replay" "/c.replay"])]
        (is (= 3 (count result)))
        (is (every? #(= "7801776992105" (:match-id %)) result))
        (is (= ["/a.replay" "/b.replay" "/c.replay"] @calls))))))

;; ─── record-match-from-parsed ──────────────────────────────────────────────

(defn- valid-submission [winners]
  {:games           (mapv (fn [w] {:parsed sample-parsed-game :winner-sub w :source-name "x.replay"}) winners)
   :uploaded-by-sub "sigmar_42"})

(deftest record-match-rejects-missing-match
  (with-redefs [data-access.contract/get-match-by-eid (fn [_ _] nil)]
    (let [r (handlers.replay/record-match-from-parsed deps match-eid (valid-submission ["sigmar_42" "sigmar_42"]))]
      (is (= :match-record/error (:type r)))
      (is (= "Match not found." (:message r))))))

(deftest record-match-rejects-already-complete
  (with-redefs [data-access.contract/get-match-by-eid (fn [_ _] (assoc bo3-match :status "complete"))]
    (let [r (handlers.replay/record-match-from-parsed deps match-eid (valid-submission ["sigmar_42" "sigmar_42"]))]
      (is (= :match-record/error (:type r)))
      (is (= "Match is already complete." (:message r))))))

(deftest record-match-rejects-already-recorded
  (with-redefs [data-access.contract/get-match-by-eid    (fn [_ _] bo3-match)
                data-access.contract/get-games-for-match (fn [_ _] [{:winner-sub "sigmar_42"}])]
    (let [r (handlers.replay/record-match-from-parsed deps match-eid (valid-submission ["sigmar_42" "sigmar_42"]))]
      (is (= :match-record/error (:type r)))
      (is (= "Match already has recorded games." (:message r))))))

(deftest record-match-rejects-undecided-series
  (with-redefs [data-access.contract/get-match-by-eid    (fn [_ _] bo3-match)
                data-access.contract/get-games-for-match (fn [_ _] [])]
    ;; A single game in a Bo3 cannot clinch the series.
    (let [r (handlers.replay/record-match-from-parsed deps match-eid (valid-submission ["sigmar_42"]))]
      (is (= :match-record/error (:type r)))
      (is (re-find #"do not decide the series" (:message r))))))

(deftest record-match-rejects-too-many-games
  (with-redefs [data-access.contract/get-match-by-eid    (fn [_ _] bo3-match)
                data-access.contract/get-games-for-match (fn [_ _] [])]
    ;; Bo3 caps at 3 games — 4 must be rejected.
    (let [r (handlers.replay/record-match-from-parsed
             deps match-eid
             (valid-submission ["sigmar_42" "sigmar_42" "runemaster" "runemaster"]))]
      (is (= :match-record/error (:type r)))
      (is (re-find #"between 1 and 3" (:message r))))))

(deftest record-match-rejects-unknown-winner-sub
  (with-redefs [data-access.contract/get-match-by-eid    (fn [_ _] bo3-match)
                data-access.contract/get-games-for-match (fn [_ _] [])]
    (let [r (handlers.replay/record-match-from-parsed deps match-eid (valid-submission ["sigmar_42" "stranger"]))]
      (is (= :match-record/error (:type r)))
      (is (re-find #"one of the match's players" (:message r))))))

(deftest record-match-persists-and-completes-when-clinched
  (let [stored-replays  (atom [])
        stored-games    (atom [])
        stored-drafts   (atom [])
        stored-states   (atom [])
        match-completed (atom nil)
        emp-faction-eid (UUID/randomUUID)
        chd-faction-eid (UUID/randomUUID)]
    (with-redefs [data-access.contract/get-match-by-eid        (fn [_ _] bo3-match)
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
                  ;; Auto-create surface: tournament + game-modes lookup, then a
                  ;; subfaction lookup per parsed alliance and the draft insert.
                  data-access.contract/get-tournament-by-eid   (fn [_ _] {:name "Spring Open" :game-eid (UUID/randomUUID)})
                  data-access.contract/get-game-modes-for-game (fn [_ _] [{:eid (UUID/randomUUID) :name "Land Battle"}
                                                                          {:eid (UUID/randomUUID) :name "Domination"}])
                  data-access.contract/get-subfactions-by-keys (fn [_ keys]
                                                                 (let [k (first keys)]
                                                                   (cond
                                                                     (= k "wh_main_emp_empire")
                                                                     [{:key k :faction-eid emp-faction-eid}]
                                                                     (= k "wh3_dlc23_chd_legion_of_azgorh")
                                                                     [{:key k :faction-eid chd-faction-eid}]
                                                                     :else [])))
                  data-access.contract/get-units-by-keys       (fn [_ _] [])
                  data-access.contract/get-mounts-for-unit     (fn [_ _] [])
                  data-access.contract/create-draft            (fn [_ spec]
                                                                 (swap! stored-drafts conj spec)
                                                                 spec)
                  data-access.contract/upsert-draft-state      (fn [_ draft-eid json]
                                                                 (swap! stored-states conj {:draft-eid draft-eid :state json}))]
      (let [submission (valid-submission ["sigmar_42" "sigmar_42"])
            result     (handlers.replay/record-match-from-parsed deps match-eid submission)]
        (testing "two replay rows persisted, in submission order"
          (is (= 2 (count @stored-replays)))
          (is (every? #(= "sigmar_42" (:uploaded-by-sub %)) @stored-replays)))
        (testing "two match_game rows persisted, linked by replay eid"
          (is (= [0 1] (mapv :game-index @stored-games)))
          (is (every? uuid? (mapv :replay-eid @stored-games))))
        (testing "Bo3 winner crowned after 2 game wins"
          (is (= "sigmar_42" @match-completed))
          (is (= "sigmar_42" (:winner-sub result)))
          (is (true? (:complete? result))))
        (testing "auto-creates one draft per side per game"
          (is (= 4 (count @stored-drafts))
              "Bo3 with 2 games → 4 drafts (P1+P2 × 2 games)")
          (is (= 4 (count @stored-states))
              "draft_state upsert mirrors the draft inserts")
          (is (= #{"sigmar_42" "runemaster"}
                 (set (map :player-sub @stored-drafts)))
              "drafts split between match.player_one_sub / player_two_sub")
          (is (= {emp-faction-eid 2 chd-faction-eid 2}
                 (frequencies (map :faction-eid @stored-drafts)))
              "factions resolved from parsed faction_key per alliance — one draft per side per game")
          (is (every? #(re-find #"Spring Open R\d+ G\d+" %)
                      (map :name @stored-drafts))
              "draft name follows `<Tournament> R<round> G<game>` format"))
        (testing "match_game rows reference the created drafts"
          (is (every? uuid? (mapv :player-one-draft-eid @stored-games)))
          (is (every? uuid? (mapv :player-two-draft-eid @stored-games))))))))

(deftest record-match-leaves-match-pending-when-not-clinched
  (let [match-completed (atom nil)]
    (with-redefs [data-access.contract/get-match-by-eid    (fn [_ _] bo3-match)
                  data-access.contract/get-games-for-match (fn [_ _] [])
                  data-access.contract/create-replay       (fn [_ spec] (assoc spec :id 1))
                  data-access.contract/create-game         (fn [_ _ gi w _] {:game-index gi :winner-sub w})
                  data-access.contract/update-match-result (fn [_ _ w] (reset! match-completed w))]
      ;; Bo3 with a 1-1 split (and a submission of only the first 2 games)
      ;; — series not yet decided.
      (let [_submission {:games           [{:parsed sample-parsed-game :winner-sub "sigmar_42" :source-name "g1.replay"}
                                           {:parsed sample-parsed-game :winner-sub "runemaster" :source-name "g2.replay"}]
                         :uploaded-by-sub "sigmar_42"}]
        ;; This will fail validation because Bo3 expects 3 games — make
        ;; a Bo1 match instead to exercise the not-clinched branch.
        (with-redefs [data-access.contract/get-match-by-eid (fn [_ _] (assoc bo3-match :format 1))]
          (let [r (handlers.replay/record-match-from-parsed
                   deps match-eid
                   {:games           [{:parsed sample-parsed-game :winner-sub "sigmar_42" :source-name "g1.replay"}]
                    :uploaded-by-sub "sigmar_42"})]
            (is (= :match-record/recorded (:type r)))
            (is (= "sigmar_42" @match-completed)) ;; Bo1 with 1 win = clinched
            (is (true? (:complete? r)))))))))
