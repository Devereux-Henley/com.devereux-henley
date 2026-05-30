(ns com.devereux-henley.rts-web.web.tournament.view
  "Web handlers for tournament pages and the player-console per-game
  replay submission flow. Standard tournament/competitive views are
  HTMX-rendered; replay parse + submit live under /actions and return
  player-console-styled fragments."
  (:require
   [camel-snake-kebab.core :as csk]
   [camel-snake-kebab.extras :as cske]
   [clojure.set :as set]
   [clojure.string :as str]
   [com.devereux-henley.rts-data-access.contract :as db]
   [com.devereux-henley.rts-domain.contract :as domain]
   [com.devereux-henley.rts-web.render :as render]
   [com.devereux-henley.rts-web.web.tournament.share :as web.tournament.share]
   [com.devereux-henley.rts-web.web.view :as web.view]
   [com.devereux-henley.schema.contract :as schema.contract]
   [integrant.core]
   [jsonista.core :as jsonista]
   [malli.core :as m]
   [taoensso.timbre :as log]))

;; ─── Tournament list / detail / config views ────────────────────────────────

(defn- enrich-tournament-with-league
  "Adds :league-name and :season-display-name to a tournament when it carries
   a :league-eid / :season-eid, by reading from the supplied lookup maps."
  [eid->league eid->season tournament]
  (cond-> tournament
    (:league-eid tournament) (assoc :league-name (get-in eid->league [(:league-eid tournament) :name]))
    (:season-eid tournament) (assoc :season-display-name (get-in eid->season [(:season-eid tournament) :display-name]))))

(defmethod integrant.core/init-key ::competitive-view
  [_init-key dependencies]
  (fn [request]
    (let [game-eid             (:game-eid (:game-context request))
          tournaments          (domain/get-tournaments-for-game dependencies game-eid)
          leagues              (domain/get-leagues-for-game dependencies game-eid)
          eid->league          (into {} (map (juxt :eid identity) leagues))
          ;; Pre-fetch the seasons for every league referenced by tournaments so we
          ;; can label tournament cards without N+1 calls.
          referenced-leagues   (distinct (keep :league-eid tournaments))
          eid->season          (into {}
                                     (mapcat (fn [leid]
                                               (map (juxt :eid identity)
                                                    (domain/get-seasons-for-league dependencies leid)))
                                             referenced-leagues))
          enriched-tournaments (mapv (fn [t]
                                       (let [state   (domain/get-tournament-state dependencies (:eid t))
                                             entries (domain/get-entries dependencies (:eid t))]
                                         (->> (assoc t
                                                     :status      (:status state)
                                                     :entry-count (count entries))
                                              (enrich-tournament-with-league eid->league eid->season))))
                                     tournaments)
          enriched-leagues     (mapv (fn [l]
                                       (let [current-season (domain/get-current-season-for-league dependencies (:eid l))
                                             tcount         (count (filter #(= (:eid l) (:league-eid %)) tournaments))]
                                         (assoc l
                                                :current-season current-season
                                                :tournament-count tcount)))
                                     leagues)]
      {:status 200
       :body   (render/render-view "competitive.html"
                                   (assoc (web.view/base-context request)
                                          :tournaments enriched-tournaments
                                          :leagues enriched-leagues))})))

(defmethod integrant.core/init-key ::create-tournament-view
  [_init-key dependencies]
  (fn [request]
    (let [game-eid (:game-eid (:game-context request))
          leagues  (domain/get-leagues-for-game dependencies game-eid)]
      {:status 200
       :body   (render/render-view "create-tournament.html"
                                   (assoc (web.view/base-context request)
                                          :tournament-eid   (random-uuid)
                                          :leagues          leagues
                                          :timezones        domain/common-timezones
                                          :default-timezone domain/default-timezone))})))

(defmethod integrant.core/init-key ::tournament-phase-form-view
  [_init-key _dependencies]
  (fn [request]
    (let [eid (get-in request [:parameters :path :eid])]
      {:status  200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body    (render/render-component "tournament-phase-row.html"
                                         (assoc (web.view/base-context request) :tournament-eid eid))})))

(defmethod integrant.core/init-key ::phase-panel-view
  [_init-key dependencies]
  (fn [{{{:keys [eid phase-index]} :path} :parameters
        :as                               _request}]
    (if-let [ctx (web.tournament.share/build-phase-context dependencies eid phase-index)]
      {:status  200
       :headers {"Content-Type" "text/html; charset=utf-8"}
       :body    (render/render-component "tournament-phase.html" {:data ctx})}
      {:status 404
       :body   {:type :missing/resource :name "tournament-phase" :id phase-index}})))

(defmethod integrant.core/init-key ::round-row-view
  [_init-key _dependencies]
  (fn [_request]
    {:status  200
     :headers {"Content-Type" "text/html; charset=utf-8"}
     :body    (render/render-component "tournament-round.html" {})}))

(defmethod integrant.core/init-key ::tournament-view
  [_init-key dependencies]
  (fn [request]
    (web.view/render-entity-view request "tournament-index.html"
                                 (domain/tournament-view-model dependencies
                                                               (-> request :parameters :path :eid)
                                                               (-> request :ory-session :identity :id)))))

;; ─── Player console (check-in + series) ─────────────────────────────────────
;;
;; Static demo surface for the new player experience. Until check-in,
;; lobby-code, and per-game replay-submission state machines are modelled in
;; the domain, these handlers return hand-rolled view-models that match the
;; design's QF1 Bo5 mid-series state.

(def ^:private demo-player-me
  {:handle        "sigmar_42" :name   "Sigmar" :faction-name "The Empire" :faction-sigil "✠"
   :faction-color "#c4a04a"   :record "3-0"    :group        "A"          :seed          "A1"})

(def ^:private demo-player-opponent
  {:handle        "chaos_undivided" :name          "Archaon" :faction-name "Warriors of Chaos"
   :faction-sigil "✷"               :faction-color "#b52424" :record       "2-1"               :group "B" :seed "B2"})

(def ^:private demo-match-label "QF 1")

(def ^:private demo-games
  [{:num          1           :map          "Battle for Eight Peaks" :result "W"      :winner "sigmar_42"
    :submitted-by "sigmar_42" :confirmed-by "chaos_undivided"        :size   "4.2 MB"}
   {:num          2                 :map          "Path of the Old Ones" :result "L"      :winner "chaos_undivided"
    :submitted-by "chaos_undivided" :confirmed-by "sigmar_42"            :size   "5.8 MB"}
   {:num          3           :map          "Karak Kadrin Crossing" :result "W"      :winner "sigmar_42"
    :submitted-by "sigmar_42" :confirmed-by "chaos_undivided"       :size   "3.7 MB"}
   {:num          4                 :map          "Black Crag Foothills" :result "L"      :winner "chaos_undivided"
    :submitted-by "chaos_undivided" :confirmed-by "sigmar_42"            :size   "6.1 MB"}
   {:num          5   :map          "Skull Pass — Winter" :result nil      :winner nil
    :submitted-by nil :confirmed-by nil                   :size   "5.2 MB"}])

(def ^:private demo-path
  [{:stage "Group Stage A" :label "3-0" :state "done" :detail "Top seed Group A"}
   {:stage "QF 1" :label "vs chaos_undivided" :state "active" :detail "Bo5 · live"}
   {:stage "SF 1" :label "vs QF2 winner" :state "pending" :detail "Bo5 · 20:30 UTC"}
   {:stage "Grand Final" :label "vs SF2 winner" :state "pending" :detail "Bo7 · May 21"}])

(defn- decorate-game
  "Adds per-game UI flags + a derived replay filename so templates stay flat."
  [{:keys [num map result] :as game} current-num current-step]
  (let [slug    (-> (or map "")
                    str/lower-case
                    (str/replace #"[^a-z0-9]+" "_")
                    (str/replace #"^_|_$" ""))
        current (and (= num current-num) (nil? result))
        cls     (cond
                  (= result "W")        "series-game--win"
                  (= result "L")        "series-game--loss"
                  current               (str "series-game--current series-game--" current-step)
                  :else                 "series-game--pending")]
    (assoc game
           :filename (str "qf1_g" num "_" slug ".rep")
           :current? current
           :i-won? (= result "W")
           :pip (cond (= result "W") "w" (= result "L") "l" current "live" :else "pending")
           :pip-label (cond (= result "W") "W" (= result "L") "L" current "●" :else "·")
           :css cls)))

(defn- series-view-model
  [current-step]
  (let [current-num           (domain/series-current-game-num demo-games)
        games                 (mapv #(decorate-game % current-num current-step) demo-games)
        settled               (filterv :result games)
        total-games           (count games)
        {:keys [wins losses]} (domain/series-win-counts games)
        current-game          (or (->> games (filter :current?) first :num) total-games)
        current-map           (when (pos? current-game)
                                (-> games (nth (dec current-game)) :map))
        my-clinches?          (domain/series-clinches? wins total-games)
        opp-clinches?         (domain/series-clinches? losses total-games)
        next-round            "Semifinal 1"
        next-game-text        (str "Game " (inc current-game) " unlocks")
        round-opens-text      (str next-round " lobby opens")
        if-i-win              (if my-clinches? round-opens-text next-game-text)
        if-opp-win            (if opp-clinches? round-opens-text next-game-text)
        generic-next          (if (= if-i-win if-opp-win) if-i-win "the series advances")]
    {:match-label          demo-match-label
     :me                   demo-player-me
     :opponent             demo-player-opponent
     :games                games
     :settled-games        settled
     :total-games          total-games
     :current-game         current-game
     :current-map          current-map
     :my-wins              wins
     :opp-wins             losses
     :my-leading?          (> wins losses)
     :opp-leading?         (> losses wins)
     :current-step         current-step
     :step-live?           (= current-step "live")
     :step-declare?        (= current-step "declare")
     :step-submitting?     (= current-step "submitting")
     :step-opp-uploading?  (= current-step "opp_uploading")
     :step-label           (case current-step
                             "live"          (str "Game " current-game " of " total-games " · in progress")
                             "declare"       (str "Game " current-game " ended · declare winner")
                             "submitting"    (str "Game " current-game " · submitting replay")
                             "opp_uploading" (str "Game " current-game " · opponent uploading replay")
                             (str "Game " current-game " of " total-games))
     :step-subtitle        (case current-step
                             "live"          "Live in the series lobby"
                             "declare"       "Game finished · winner submits the replay"
                             "submitting"    "You won this game · upload + submit"
                             "opp_uploading" (str "You lost this game · waiting for "
                                                  (:handle demo-player-opponent))
                             "Live in the series lobby")
     :if-i-win-next        if-i-win
     :if-opp-wins-next     if-opp-win
     :generic-next         generic-next
     :continues-if-i-win   (not my-clinches?)
     :continues-if-opp-win (not opp-clinches?)
     :next-round-label     next-round}))

(defmethod integrant.core/init-key ::player-check-in-view
  [_init-key dependencies]
  (fn [request]
    (let [tournament (web.tournament.share/get-tournament-by-eid dependencies (-> request :parameters :path :eid))]
      (web.view/render-entity-view request "player-check-in.html"
                                   (if (= :missing/resource (:type tournament))
                                     tournament
                                     {:data                 tournament
                                      :match-label          demo-match-label
                                      :me                   demo-player-me
                                      :opponent             demo-player-opponent
                                      :path                 demo-path
                                      :check-in-window      "Window closes at 13:55 UTC · 7 min remaining · one check-in covers all five games"
                                      :opponent-checked-in? true})))))

(defn- find-current-player-match
  "Returns the earliest pending match (lowest phase-index, then round-index)
   in the tournament where `user-sub` is one of the players, or nil if none
   exists. Ordering matters in double-elim, where a player can have
   simultaneous Winners- and Losers-bracket matches pending."
  [dependencies tournament-eid user-sub]
  (->> (domain/get-matches-for-tournament dependencies tournament-eid)
       (filter (fn [m]
                 (and (= "pending" (:status m))
                      (or (= user-sub (:player-one-sub m))
                          (= user-sub (:player-two-sub m))))))
       (sort-by (juxt :phase-index :round-index))
       first))

(def ^:private parse-log-stagger-ms 280)

(def ^:private parse-log-labels
  ["Reading replay"
   "Verifying header"
   "Detecting players & factions"
   "Resolving draft compositions"
   "Reading map & duration"])

(defn- single-game-parse-log
  "Five staggered log rows for the parse animation (one game)."
  []
  (vec (map-indexed (fn [idx label] {:label label :delay-ms (* idx parse-log-stagger-ms)})
                    parse-log-labels)))

(defmethod integrant.core/init-key ::player-series-view
  [_init-key dependencies]
  (fn [request]
    (let [tournament (web.tournament.share/get-tournament-by-eid dependencies (-> request :parameters :path :eid))]
      (web.view/render-entity-view
       request "player-series.html"
       (if (= :missing/resource (:type tournament))
         tournament
         (let [step          (or (get-in request [:parameters :query :step]) "live")
               step          (if (#{"live" "declare" "submitting" "opp_uploading"} step) step "live")
               user-sub      (get-in request [:ory-session :identity :id])
               current-match (find-current-player-match dependencies (:eid tournament) user-sub)]
           (cond-> (assoc (series-view-model step)
                          :data                tournament
                          :parse-log-rows      (single-game-parse-log)
                          ;; Defer the swap so the parse animation plays through
                          ;; even when the parser returns instantly. Hold back
                          ;; the last few rows' stagger so the response lands
                          ;; mid-animation rather than after it.
                          :parse-swap-delay-ms (* 2 parse-log-stagger-ms))
             current-match (assoc :current-match current-match
                                  :match-eid (:eid current-match)))))))))

;; ─── Post-match modal helpers ───────────────────────────────────────────────

(defn- parsed->snake
  "Internal kebab-case keys from the parser get round-tripped through the
  client (echoed back to the commit endpoint).  Emit snake_case to keep the
  JSON natural for the JS modal and to match the Rust binary's wire format."
  [parsed]
  (cske/transform-keys csk/->snake_case_string parsed))

(defn- collect-unit-keys
  "Walks a parsed map's alliances → armies → units and returns the set of
  every non-blank engine key."
  [parsed]
  (->> (:alliances parsed)
       (mapcat :armies)
       (mapcat :units)
       (keep :key)
       (remove empty?)
       set))

(defn- key-prefix-candidates
  "Successively shorter prefixes of `k` produced by stripping one trailing
  `_<token>` at a time, ordered longest-first.  Used so a parser-emitted
  mount-suffixed key (e.g. `wh3_dlc23_chd_cha_sorcerer_prophet_fire_great_taurus`)
  resolves against the base unit row whose `unit.key` is the un-mounted
  variant (`wh3_dlc23_chd_cha_sorcerer_prophet_fire`)."
  [k]
  (let [parts (str/split k #"_")]
    (when (> (count parts) 1)
      (mapv (fn [n] (str/join "_" (take n parts)))
            (range (dec (count parts)) 0 -1)))))

(defn- resolve-key
  "Exact lookup, then longest-prefix fallback against `key->row` so mount
  variants enrich against their base unit row."
  [key->row k]
  (or (get key->row k)
      (some #(get key->row %) (key-prefix-candidates k))))

(defn- enrich-unit
  "Adds resolved unit data (name, cost, category, level/adjusted-cost,
  Mark of Chaos) to a unit map when its engine key has a matching DB row.

  When the parser emitted a non-zero `:cost` (engine-resolved final cost
  including mount/mark/lore/veterancy/armory adders), it wins as
  `:adjusted-cost` — the prefix-fallback resolution returns the un-mounted
  base row, whose seed cost is too low for variants like
  `..._steam_tank` or `..._great_taurus`. Falls back to
  `(apply-level-cost base-cost level-row)` for replays parsed by older
  binaries that don't emit `:cost`. Leaves the map unchanged when no row
  resolves so the client can fall back to the raw key."
  [key->row level->cost-row {:keys [key] :as unit}]
  (let [level       (or (:level unit) 0)
        parsed-cost (:cost unit)]
    (if-let [row (resolve-key key->row key)]
      (assoc unit
             :name                 (:name row)
             :cost                 (:cost row)
             :level                level
             :adjusted-cost        (if (and parsed-cost (pos? parsed-cost))
                                     parsed-cost
                                     (domain/apply-level-cost (:cost row) (get level->cost-row level)))
             :unit-category-name   (:unit-category-name row)
             :unit-type-name       (:unit-type-name row)
             :unit-eid             (:eid row)
             :mark                 (:mark row)
             :family-variant-count (:family-variant-count row))
      (assoc unit :level level))))

(defn- enrich-parsed
  "Threads `enrich-unit` through every unit in the parsed structure."
  [key->row level->cost-row parsed]
  (update parsed :alliances
          (fn [alliances]
            (mapv (fn [alliance]
                    (update alliance :armies
                            (fn [armies]
                              (mapv (fn [army]
                                      (update army :units
                                              (fn [units]
                                                (mapv (partial enrich-unit key->row level->cost-row) units))))
                                    armies))))
                  alliances))))

(defn- resolve-units
  "Builds a `key → row` map for every unit key referenced by the parsed
  games.  Includes prefix candidates so a parser-emitted mount-suffixed
  key resolves against the base unit row at enrich time."
  [dependencies parsed-vec]
  (let [keys     (apply set/union (map collect-unit-keys parsed-vec))
        all-keys (into keys (mapcat key-prefix-candidates) keys)]
    (if (empty? all-keys)
      {}
      (->> (db/units-by-keys (:datalog-connection dependencies) all-keys)
           (into {} (map (juxt :key identity)))))))

(defn- collect-faction-keys
  "Walks a parsed map's alliances and returns the set of every non-blank
  `:faction-key` (the parser's engine-level subfaction id)."
  [parsed]
  (->> (:alliances parsed)
       (keep :faction-key)
       (remove empty?)
       set))

(defn- resolve-faction-keys
  "Builds a `subfaction-key → resolution-row` map covering every faction
  key referenced across the parsed games.  Logs a warning for any keys
  the DB couldn't resolve so missing seed data shows up in the operator
  log instead of silently rendering as the raw engine string."
  [dependencies parsed-vec]
  (let [keys (apply set/union (map collect-faction-keys parsed-vec))]
    (if (empty? keys)
      {}
      (let [rows       (db/subfactions-by-keys (:datalog-connection dependencies) keys)
            resolved   (into {} (map (juxt :key identity)) rows)
            unresolved (set/difference keys (set (map :key rows)))]
        (when (seq unresolved)
          (log/warn "Unresolved faction-keys; rendering raw engine ids."
                    {:keys (sort unresolved)}))
        resolved))))

(defn- faction-display
  "Renders the parent race name for a resolved subfaction row (e.g.
  `wh_main_emp_empire` → `The Empire`).  The engine subfaction name (e.g.
  `Reikland`) is intentionally dropped: in comp play the army's race is
  what matters; the lord's specific subfaction is flavour.  Returns the
  raw engine key when no row resolved so a missing-data case is
  debuggable rather than blank."
  [faction-key key->row]
  (or (get-in key->row [faction-key :faction-name])
      faction-key))

(defn- collect-game-files
  "Pulls multipart parts named `game-0`, `game-1`, … in order, up to but
  not including the first index that has no part. Returns a vector of
  {:source-name <original filename> :file-path <tempfile abs path>}."
  [multipart-params]
  (loop [index 0 acc []]
    (if-let [part (get multipart-params (str "game-" index))]
      (recur (inc index)
             (conj acc {:source-name (:filename part)
                        :file-path   (.getAbsolutePath (:tempfile part))}))
      acc)))

(defn- snake->kebab
  "Re-keywordizes a JSON-decoded parsed map back to the kebab-case
  keywords the domain layer expects. The parsed JSON travels through the
  modal as snake_case (matching the Rust binary's wire format)."
  [m] (cske/transform-keys csk/->kebab-case-keyword m))

(defn- format-played-at
  "Renders the parser's `:played-at` map as `YYYY-MM-DD HH:MM`. Returns nil
  for nil/string inputs (string is shown verbatim by the template)."
  [played-at]
  (when (map? played-at)
    (format "%04d-%02d-%02d %02d:%02d"
            (:year played-at) (:month played-at) (:day played-at)
            (:hour played-at) (:minute played-at))))

(defn- army->units
  "Maps a single army's units into the per-unit shape the review template
  expects (display name, cost when enriched, lord flag, tooltip).

  The `enriched?` branch and `or`/`if` fallbacks below cover parser keys
  that don't resolve to a DB unit row. Once #45 lands and key resolution
  is guaranteed, the fallbacks become dead code — see #49 for the
  cleanup checklist."
  [army]
  (mapv (fn [{:keys [key name cost adjusted-cost level unit-category-name unit-type-name unit-eid mark family-variant-count]}]
          (let [enriched?   (some? name)
                ;; FALLBACK (#49): unresolved parser key shows the raw engine key.
                display     (if enriched? name key)
                ;; FALLBACK (#49): missing category data falls through to type, then em-dash.
                category    (or unit-category-name unit-type-name "—")
                is-lord?    (= "lord" (some-> unit-category-name str/lower-case))
                ;; level 0 → no chevron; otherwise show pip count for the template.
                shown-cost  (or adjusted-cost cost)
                ;; Surface the badge only when the family has more than
                ;; one mark variant — for mono-mark families like
                ;; "Bloodletters of Khorne" or "Herald of Khorne" the
                ;; name itself already implies the mark.
                mark-shown? (and mark
                                 (some? family-variant-count)
                                 (> family-variant-count 1))]
            {:key           key
             :unit-eid      unit-eid
             :display       display
             :cost          cost
             :adjusted-cost adjusted-cost
             :level         level
             ;; The template needs the pip-count and a boolean toggle so the
             ;; chevron overlay only renders when the unit was leveled.
             :leveled?      (pos? level)
             :is-lord       is-lord?
             :mark          mark
             :mark-shown    mark-shown?
             :mark-label    (when mark (str/capitalize mark))
             ;; FALLBACK (#49): tooltip omits cost when the DB row is missing.
             :tooltip       (cond
                              (and shown-cost (pos? level))
                              (str category " · " shown-cost " pts (rank " level ")")

                              shown-cost
                              (str category " · " shown-cost " pts")

                              :else category)}))
        (:units army)))

(defn- section-totals
  "Per-section count/cost pair mirroring `alliance-totals` at section
  scope: cost in pts when every unit in the section is enriched, otherwise
  the section's army-level :force-value sum (model count). The pts sum
  uses each unit's veterancy-adjusted cost so the header reflects what
  the player actually paid.

  The model-count branch is a FALLBACK (#49) for partial enrichment — once
  #45 guarantees every parser key matches a DB unit row, this collapses
  to just the pts branch."
  [armies units]
  (let [enriched-count (count (filter :cost units))]
    (if (and (pos? enriched-count) (= enriched-count (count units)))
      {:section-num  (reduce + 0 (map (fn [u] (or (:adjusted-cost u) (:cost u))) units))
       :section-unit "pts"}
      {:section-num  (reduce + 0 (keep :force-value armies))
       :section-unit "models"})))

(defn- build-section
  "Composes a section context (label, units, count, cost/model-count)
  for a contiguous group of armies that belong to either the Main Army
  or the Reinforcements bucket."
  [section-key armies]
  (let [label (case section-key
                "main"           "Main Army"
                "reinforcements" "Reinforcements")
        units (vec (mapcat army->units armies))]
    (merge {:section            section-key
            :section-label      label
            :section-units      units
            :section-unit-count (count units)}
           (section-totals armies units))))

(defn- alliance->sections
  "Splits an alliance's armies into a Main Army section (the first
  army) and a Reinforcements section (everything after). The Reinforcements
  section is omitted when the alliance only carries one army; the Main
  Army section is omitted when the alliance carries no armies at all."
  [alliance]
  (let [armies (vec (:armies alliance))
        main   (when (seq armies) [(first armies)])
        reinf  (vec (rest armies))]
    (cond-> []
      main        (conj (build-section "main" main))
      (seq reinf) (conj (build-section "reinforcements" reinf)))))

(defn- alliance-totals
  "Returns the {:total-num … :total-unit …} pair shown in the draft head.
  When every unit is enriched, reports total point cost using each unit's
  veterancy-adjusted cost; otherwise falls back to model count so the
  header isn't blank."
  [alliance units]
  (let [enriched-count (count (filter :cost units))]
    (if (and (pos? enriched-count) (= enriched-count (count units)))
      {:total-num  (reduce + 0 (map (fn [u] (or (:adjusted-cost u) (:cost u))) units))
       :total-unit "pts"}
      {:total-num  (or (:model-count alliance)
                       (reduce + 0 (keep :force-value (:armies alliance)))
                       0)
       :total-unit "models"})))

(defn- side-context
  "Builds the per-player side struct for one game: handle, faction display,
  side-level totals, commander, and a vector of section contexts (Main
  Army + optional Reinforcements). `side-key` (\"p1\" / \"p2\") is woven
  into the struct so the template can build unique heading IDs for
  `aria-labelledby` without a parent loop variable.  `faction-key->row` is
  the resolver map produced by `resolve-faction-keys`; when an alliance's
  engine key is in the map, `:faction-display` renders the human-readable
  name (e.g. `The Empire`, `Chaos Dwarfs → The Legion of Azgorh`); otherwise
  the raw engine key is shown so missing seed data is visible to operators."
  [side-key handle alliance faction-key->row]
  (let [armies    (vec (:armies alliance))
        sections  (alliance->sections alliance)
        all-units (vec (mapcat :section-units sections))
        totals    (alliance-totals alliance all-units)
        commander (:commander-display (first armies))]
    (merge totals
           {:side-key          side-key
            :handle            handle
            :faction-key       (:faction-key alliance)
            :faction-display   (faction-display (:faction-key alliance) faction-key->row)
            :sections          sections
            :commander-display (or commander "")})))

(defn- build-game-context
  "Shapes a parsed game into the row the review template iterates over.
  Resolves which alliance maps to which player using the parser's
  uploader-local-alliance-index plus the viewer's identity (the uploader
  is whoever is using the modal). The hidden parsed-json carries the
  snake_case form back to the submit endpoint untouched."
  [match viewer-sub game-index source-name parsed faction-key->row]
  (let [a0               (get-in parsed [:alliances 0] {})
        a1               (get-in parsed [:alliances 1] {})
        viewer-local-idx (:uploader-local-alliance-index parsed)
        viewer-is-p1?    (= viewer-sub (:player-one-sub match))
        ;; viewer-is-p1? + viewer-local-idx pair determines the mapping.
        ;; If viewer is p1 and viewer's alliance is index 0, p1=a0.
        ;; If viewer is p1 and viewer's alliance is index 1, p1=a1.
        ;; If viewer is p2, swap.
        p1-alliance      (if (= viewer-is-p1? (zero? (or viewer-local-idx 0))) a0 a1)
        p2-alliance      (if (identical? p1-alliance a0) a1 a0)]
    {:game-index     game-index
     :game-num       (inc game-index)
     :parsed-json    (jsonista/write-value-as-string (parsed->snake parsed))
     :source-name    source-name
     :match-id       (:match-id parsed)
     :played-at      (format-played-at (:played-at parsed))
     :parser-format  (:format parsed)
     :p1-model-count (:model-count p1-alliance)
     :p2-model-count (:model-count p2-alliance)
     :p1             (side-context "p1" (:player-one-sub match) p1-alliance faction-key->row)
     :p2             (side-context "p2" (:player-two-sub match) p2-alliance faction-key->row)}))

(defn- error-fragment
  [message]
  {:status  422
   :headers {"Content-Type" "text/html; charset=utf-8"}
   :body    (str "<section class=\"pm-error\" role=\"alert\">"
                 (-> message
                     (str/replace "&" "&amp;")
                     (str/replace "<" "&lt;")
                     (str/replace ">" "&gt;"))
                 "</section>")})

;; ─── Player console replay submission ──────────────────────────────────────
;;
;; Per-game submit flow for the player console: parses one replay, returns
;; the review fragment, then on submit persists the next match_game row.

(defn- build-review-context
  "Shapes parsed + enriched replay data into the player-console review
   fragment context."
  [dependencies match parsed source-name viewer-sub]
  (let [key->row     (resolve-units dependencies [parsed])
        level-costs  (db/unit-level-costs (:datalog-connection dependencies))
        enriched     (enrich-parsed key->row level-costs parsed)
        faction->row (resolve-faction-keys dependencies [parsed])
        existing     (domain/get-games-for-match dependencies (:eid match))
        game-index   (count existing)
        game-num     (inc game-index)
        ctx          (build-game-context match viewer-sub game-index source-name enriched faction->row)
        ;; Section caps come from the tournament's game-mode, matched against the
        ;; replay's victory-condition. Each section's max is the relevant budget
        ;; (main → :draft-value, reinforcements → :reinforcement-value).
        tournament   (domain/get-tournament-by-eid dependencies (:tournament-eid match))
        game-modes   (db/game-modes-for-game (:datalog-connection dependencies) (:game-eid tournament))
        game-mode    (domain/pick-game-mode game-modes (:victory-condition enriched))
        section-max  (fn [section-key]
                       (case section-key
                         "main"           (:draft-value game-mode)
                         "reinforcements" (:reinforcement-value game-mode)
                         (:draft-value game-mode)))
        with-max     (fn [side]
                       (update side :sections
                               #(mapv (fn [s] (assoc s :section-max (section-max (:section s)))) %)))
        me-key       (if (= viewer-sub (:player-one-sub match)) :p1 :p2)
        opp-key      (if (= me-key :p1) :p2 :p1)
        me-side      (with-max (get ctx me-key))
        opp-side     (with-max (get ctx opp-key))
        ;; Convention from the design's declare step: only the winning player
        ;; uploads the replay (the losing side waits). So the uploader is the
        ;; winner. Either side can dispute later if the replay disagrees.
        winner-sub   viewer-sub
        i-won?       true]
    {:match       match
     :game-num    game-num
     :total-games (:format match)
     :source-name source-name
     :parsed-json (:parsed-json ctx)
     :match-id    (:match-id ctx)
     :played-at   (:played-at ctx)
     :map-name    (or (:map enriched) (:map-name enriched) (:battle-map enriched))
     :winner-sub  winner-sub
     :i-won?      i-won?
     :me          me-side
     :opponent    opp-side
     :pts-cap     (or (:points-cap enriched) (:max-points enriched) (:army-cap enriched))}))

(defn- participant-or-error
  "Fetches the match, validates it belongs to `tournament-eid` and that
   `viewer-sub` is one of its participants. Returns `[:ok match]` or
   `[:error fragment]` so the calling handler stays branch-flat."
  [dependencies match-eid tournament-eid viewer-sub]
  (let [match (db/match-by-eid (:datalog-connection dependencies) match-eid)]
    (cond
      (nil? match)
      [:error (error-fragment "Match not found.")]

      (not= tournament-eid (:tournament-eid match))
      [:error (error-fragment "Match does not belong to this tournament.")]

      (not (or (= viewer-sub (:player-one-sub match))
               (= viewer-sub (:player-two-sub match))))
      [:error (error-fragment "Only match participants can act on this match.")]

      :else
      [:ok match])))

(def ^:private parse-multipart-spec
  "Multipart payload must carry exactly one replay file (named `game-0`).
   `:error/fn` distinguishes the empty / too-many cases so humanize gives
   the player the message that matches what they actually did."
  (schema.contract/to-schema
   [:sequential
    {:min      1
     :max      1
     :error/fn (fn [{:keys [value]} _]
                 (if (empty? value)
                   "no replay file supplied"
                   "per-game submission accepts exactly one replay file"))}
    [:map [:source-name :string] [:file-path :string]]]))

(def ^:private submit-form-spec
  "Form fields echoed back from the review fragment. `parsed-json` is the
   hidden parser output, `winner-sub` the declared winner. Both must be
   non-blank; `source-name` is the original replay filename."
  (schema.contract/to-schema
   [:map
    [:parsed-json [:and
                   [:string {:error/message "parsed replay payload missing"}]
                   [:fn {:error/message "parsed replay payload is blank"} (complement str/blank?)]]]
    [:winner-sub  [:and
                   [:string {:error/message "winner not declared"}]
                   [:fn {:error/message "winner not declared"} (complement str/blank?)]]]
    [:source-name {:optional true} [:maybe :string]]]))

(defmethod integrant.core/init-key ::player-replay-parse-fragment
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid match-eid]} :path} :parameters
        multipart-params                           :multipart-params
        session                                    :ory-session
        :as                                        _request}]
    (let [viewer-sub  (get-in session [:identity :id])
          files       (collect-game-files multipart-params)
          [status v]  (participant-or-error dependencies match-eid tournament-eid viewer-sub)
          shape-error (schema.contract/explain->message
                       (m/explain parse-multipart-spec files))]
      (cond
        (= :error status)
        v

        shape-error
        (error-fragment shape-error)

        :else
        (try
          (let [match                           v
                {:keys [source-name file-path]} (first files)
                parsed                          (domain/parse-replay-file dependencies file-path)
                tournament                      (domain/get-tournament-by-eid dependencies tournament-eid)
                ctx                             (build-review-context dependencies
                                                                      (assoc match :game-eid (:game-eid tournament))
                                                                      parsed source-name viewer-sub)]
            {:status  200
             :headers {"Content-Type" "text/html; charset=utf-8"}
             :body    (render/render-component "player-replay-review-fragment.html" ctx)})
          (catch Exception e
            (error-fragment (str "Replay parse failed: " (.getMessage e)))))))))

(defmethod integrant.core/init-key ::player-replay-submit-fragment
  [_init-key dependencies]
  (fn [{{{:keys [tournament-eid match-eid]} :path} :parameters
        session                                    :ory-session
        form-params                                :form-params
        :as                                        _request}]
    (let [uploader    (get-in session [:identity :id])
          form        {:parsed-json (get form-params "parsed-json")
                       :winner-sub  (get form-params "winner-sub")
                       :source-name (get form-params "source-name")}
          [status v]  (participant-or-error dependencies match-eid tournament-eid uploader)
          shape-error (schema.contract/explain->message
                       (m/explain submit-form-spec form))]
      (cond
        (= :error status)
        v

        shape-error
        (error-fragment shape-error)

        :else
        (let [parsed (-> (:parsed-json form)
                         (jsonista/read-value (jsonista/object-mapper {:decode-key-fn keyword}))
                         snake->kebab)
              result (domain/record-game-from-parsed
                      dependencies match-eid
                      {:parsed          parsed
                       :winner-sub      (:winner-sub form)
                       :source-name     (:source-name form)
                       :uploaded-by-sub uploader})]
          (case (:type result)
            :match-record/game-recorded
            (let [tournament  (domain/get-tournament-by-eid dependencies tournament-eid)
                  fresh-match (-> (db/match-by-eid (:datalog-connection dependencies) match-eid)
                                  (assoc :game-eid (:game-eid tournament)))]
              {:status  200
               :headers {"Content-Type"            "text/html; charset=utf-8"
                         "HX-Trigger-After-Settle" "match-game-recorded"}
               :body    (render/render-component
                         "player-replay-submitted-fragment.html"
                         {:match           fresh-match
                          :game-num        (inc (:game-index result))
                          :total-games     (:format fresh-match)
                          :winner-sub      (:winner-sub result)
                          :i-won?          (= (:winner-sub result) uploader)
                          :opponent-sub    (if (= uploader (:player-one-sub fresh-match))
                                             (:player-two-sub fresh-match)
                                             (:player-one-sub fresh-match))
                          :match-complete? (:match-complete? result)
                          :match-winner    (:match-winner result)})})

            :match-record/error
            (error-fragment (:message result))))))))
