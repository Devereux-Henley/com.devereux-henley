(ns com.devereux-henley.rts-web.web.tournament.view
  "Web handlers for tournament pages and the post-match modal.

  The tournament/competitive views drive the standard HTMX-rendered pages.

  The post-match modal is HTMX-driven with a two-phase flow:

    1. POST /view/match-record/:match-eid/parse — multipart with N
       `.replay` files. Parses each via the Rust binary, enriches units
       from the unit table, and returns the Step 3 review fragment HTML
       (with hidden parsed JSON in form fields). No DB writes.
    2. POST /view/match-record/:match-eid/submit — form-encoded with the
       hidden parsed JSON echoed back plus per-game `winner-sub-N`.
       Persists replays + match_game rows + match completion, returns the
       Step 4 submitted fragment HTML.

  GET /view/match-record/:match-eid/index.html serves the modal shell."
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
   [integrant.core]
   [jsonista.core :as jsonista]
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

(def ^:private phase-type-labels
  {"single-elimination" "Single Elimination"
   "double-elimination" "Double Elimination"
   "swiss"              "Swiss"
   "round-robin"        "Round Robin"})

(defn- column-label
  "Bracket column heading. Branches by bracket type so DE's losers/grand-final
   rounds get appropriate labels instead of `Final` clashing with WB."
  [bracket round-index total-rounds]
  (let [distance-from-end (- (dec total-rounds) round-index)]
    (case bracket
      "grand-final" "Grand Final"
      "losers"      (case distance-from-end
                      0 "Losers Final"
                      1 "Losers Semis"
                      (str "Losers R" (inc round-index)))
      "winners"     (case distance-from-end
                      0 "Final"
                      1 "Semifinals"
                      2 "Quarterfinals"
                      (str "Round " (inc round-index)))
      ;; fallback for swiss/round-robin rounds rendered through this
      ;; same helper.
      (str "Round " (inc round-index)))))

(defn- match-label
  "Per-card label inside a bracket column."
  [bracket round-index total-rounds match-position]
  (let [distance-from-end (- (dec total-rounds) round-index)]
    (case bracket
      "grand-final" "GF"
      "losers"      (str "LB R" (inc round-index) " M" match-position)
      "winners"     (case distance-from-end
                      0 "F"
                      1 (str "SF " match-position)
                      2 (str "QF " match-position)
                      (str "R" (inc round-index) " M" match-position))
      (str "R" (inc round-index) " M" match-position))))

(defn- with-game-counts
  "Adds :p1-games-won and :p2-games-won to a match by counting :winner-sub
   in the match's games. Bracket cells display these instead of W/L."
  [dependencies match]
  (let [games (domain/get-games-for-match dependencies (:eid match))
        wins  (frequencies (keep :winner-sub games))]
    (assoc match
           :p1-games-won (get wins (:player-one-sub match) 0)
           :p2-games-won (get wins (:player-two-sub match) 0))))

(defn- match-status-subheader
  "Returns :status-primary + :status-secondary strings for a bracket match
   based on its state. The viewer renders these as a two-line subfooter
   under the card."
  [{:keys [placeholder? p1-tbd? p2-tbd? p1-games-won p2-games-won status]}]
  (let [games-played (+ (or p1-games-won 0) (or p2-games-won 0))
        awaiting?    (or placeholder?
                         (and (= "pending" status) (or p1-tbd? p2-tbd?)))]
    (cond
      awaiting?
      {:status-primary "Awaiting bracket" :status-secondary "·"}

      (= "complete" status)
      {:status-primary "Final" :status-secondary "Settled"}

      (and (= "pending" status) (pos? games-played))
      {:status-primary (str "Game " (inc games-played)) :status-secondary "Live"}

      :else
      {:status-primary "Scheduled" :status-secondary "—"})))

(defn- decorate-bracket-round
  "Annotates one round of a bracket with the labels and per-match flags
   the template needs (Selmer can't do arithmetic comparisons, so it all
   has to be precomputed). `bracket` is `winners` / `losers` /
   `grand-final` so column/match labels branch correctly for DE."
  [bracket {:keys [round total-rounds matches] :as round-group}]
  (assoc round-group
         :column-label (column-label bracket round total-rounds)
         :matches      (mapv (fn [position match]
                               (let [p1         (:player-one-sub match)
                                     p2         (:player-two-sub match)
                                     winner     (:winner-sub match)
                                     complete?  (= "complete" (:status match))
                                     base-match (assoc match
                                                       :match-label  (match-label bracket round total-rounds position)
                                                       :p1-winner?   (and complete? (= winner p1))
                                                       :p2-winner?   (and complete? (= winner p2))
                                                       :p1-loser?    (and complete? (some? p1) (not= winner p1))
                                                       :p2-loser?    (and complete? (some? p2) (not= winner p2))
                                                       :p1-tbd?      (nil? p1)
                                                       :p2-tbd?      (nil? p2)
                                                       :p2-bye?      (and (some? p1) (nil? p2))
                                                       :placeholder? (nil? (:eid match)))]
                                 (merge base-match (match-status-subheader base-match))))
                             (iterate inc 1)
                             matches)))

(defn- relabel-winners-final
  "In a double-elimination bracket the last WB round is the 'Winners Final',
   not the tournament final — that's the grand final. Override the column
   label + per-match label so the unified bracket strip reads correctly."
  [wb-rounds]
  (if (seq wb-rounds)
    (update wb-rounds (dec (count wb-rounds))
            (fn [round]
              (-> round
                  (assoc :column-label "Winners Final")
                  (update :matches
                          (fn [matches]
                            (mapv #(assoc % :match-label "WF") matches))))))
    wb-rounds))

(defn- assign-grid
  "Decorates each round-cell with grid placement metadata: `:grid-style`
   (CSS fragment for inline style), plus `:bracket-col`, `:bracket-row`,
   `:bracket-row-span` for the connector JS to pair adjacent cells."
  ([column row cell] (assign-grid column row 1 cell))
  ([column row row-span cell]
   (assoc cell
          :grid-style       (format "grid-column: %d; grid-row: %d / span %d;"
                                    column row row-span)
          :bracket-col      column
          :bracket-row      row
          :bracket-row-span row-span)))

(defn- decorate-bracket-phase
  "Annotates a phase group with grid-positioned cells for the unified
   viewer bracket. Covers SE, DE (WB on top, LB stacked below, GF as the
   final column spanning both rows), and Swiss / round-robin (flat row).

   Exposes:
   - `:bracket-cells` — the flat list of decorated round-cells, each
     carrying `:grid-style` for explicit grid placement.
   - `:bracket-cols`  — column count for `grid-template-columns`.
   - `:bracket-rows`  — row count (1 for SE/Swiss/RR, 2 for DE)."
  [phase-group]
  (let [decorated (cond-> phase-group
                    (:winners-bracket phase-group) (update :winners-bracket #(mapv (partial decorate-bracket-round "winners") %))
                    (:losers-bracket phase-group)  (update :losers-bracket  #(mapv (partial decorate-bracket-round "losers") %))
                    (:grand-final phase-group)     (update :grand-final     #(mapv (partial decorate-bracket-round "grand-final") %))
                    (:rounds phase-group)          (update :rounds          #(mapv (partial decorate-bracket-round "rounds") %)))
        de?       (boolean (or (:losers-bracket decorated) (:grand-final decorated)))
        wb        (cond-> (:winners-bracket decorated)
                    de? relabel-winners-final)
        lb        (or (:losers-bracket decorated) [])
        gf        (or (:grand-final decorated) [])
        rounds    (or (:rounds decorated) [])
        cells     (cond
                    de?
                    (let [n     (count wb)
                          ;; 3-row grid: WB on row 1, dashed divider on
                          ;; row 2, LB on row 3. GF spans all three so
                          ;; it sits flush at the right edge.
                          wb-cs (map-indexed (fn [i r] (assign-grid (inc i) 1 r)) wb)
                          ;; Drop the per-LB column label — the WB
                          ;; column above already labels each column.
                          lb-cs (map-indexed (fn [i r]
                                               (assign-grid (+ i 2) 3 (assoc r :column-label nil)))
                                             lb)
                          gf-cs (map (fn [r] (assign-grid (inc n) 1 3 r)) gf)]
                      (vec (concat wb-cs lb-cs gf-cs)))

                    (seq rounds)
                    (vec (map-indexed (fn [i r] (assign-grid (inc i) 1 r)) rounds))

                    :else
                    (vec (map-indexed (fn [i r] (assign-grid (inc i) 1 r)) wb)))
        cols      (cond
                    de?         (inc (count wb))
                    (seq rounds) (count rounds)
                    :else        (count wb))
        rows      (if de? 3 1)]
    (assoc decorated
           :bracket-cells cells
           :bracket-cols  cols
           :bracket-rows  rows
           :bracket-divider? de?)))

(def ^:private bracket-labels
  {"winners"     "Winners"
   "losers"      "Losers"
   "grand-final" "Grand Final"})

(defn- round-buckets-for-phase
  "Flattens every bucket inside a phase-group into a flat round-list,
   tagged with the bracket it came from. Covers Swiss/RR `:rounds`, SE
   `:winners-bracket`, plus DE's `:losers-bracket` and `:grand-final`."
  [phase-group]
  (let [tag-bracket (fn [bracket rounds]
                      (mapv #(assoc % :bracket-label (bracket-labels bracket))
                            (or rounds [])))]
    (concat (tag-bracket "winners"     (or (:rounds phase-group) (:winners-bracket phase-group)))
            (tag-bracket "losers"      (:losers-bracket phase-group))
            (tag-bracket "grand-final" (:grand-final phase-group)))))

(defn- pending-matches-schedule
  "Flat, round-ordered list of pending matches across every phase and
   bracket, shaped for the viewer's Schedule list. Drops placeholder
   rows (no :eid) and complete matches. First entry is marked `:up-next?`
   so the template highlights it without peeking at forloop.first."
  [matches-by-phase]
  (let [round-buckets (mapcat round-buckets-for-phase matches-by-phase)
        rows          (->> round-buckets
                           (mapcat (fn [{:keys [round phase-type matches bracket-label]}]
                                     (let [phase-label (or (phase-type-labels phase-type)
                                                           phase-type)
                                           round-label (cond
                                                         (= "Grand Final" bracket-label) bracket-label
                                                         (= "Winners"     bracket-label) (str "Round " (inc round))
                                                         :else                           (str bracket-label " R" (inc round)))]
                                       (->> matches
                                            (filter :eid)
                                            (filter #(= "pending" (:status %)))
                                            (map #(assoc %
                                                         :round-label round-label
                                                         :phase-label phase-label))))))
                           vec)]
    (if (empty? rows)
      rows
      (assoc-in rows [0 :up-next?] true))))

(defn- current-round-label
  "Returns the column-label of the bracket cell containing the next
   pending match across all phases. Used in the hero pill so the viewer
   shows e.g. `Quarterfinals` while QF matches are still being played."
  [decorated-phases up-next-eid]
  (when up-next-eid
    (let [cells (mapcat :bracket-cells decorated-phases)
          cell  (first (filter (fn [c]
                                 (some #(= up-next-eid (:eid %)) (:matches c)))
                               cells))]
      (:column-label cell))))

(defn- decorate-standings
  "Sorts standings by points descending, then annotates each row with its
   1-based rank and a boolean for whether it falls within the qualifier cut."
  [standings qualifier-count]
  (mapv (fn [rank row]
          (assoc row
                 :rank      rank
                 :advanced? (and qualifier-count (<= rank qualifier-count))))
        (iterate inc 1)
        (sort-by #(- (or (:points %) 0)) standings)))

(defmethod integrant.core/init-key ::tournament-view
  [_init-key dependencies]
  (partial web.view/standard-entity-view-handler
           (fn [eid] (web.tournament.share/get-tournament-by-eid dependencies eid))
           "tournament-index.html"
           (fn [data request]
             (let [tournament-eid       (:eid data)
                   state                (domain/get-tournament-state dependencies tournament-eid)
                   entries              (domain/get-entries dependencies tournament-eid)
                   raw-matches          (mapv (partial with-game-counts dependencies)
                                              (domain/get-matches-for-tournament dependencies tournament-eid))
                   phases               (:phases state)
                   qualifier-count      (or (:qualifier-count state) (count (:standings state)))
                   user-sub             (get-in request [:ory-session :identity :id])
                   has-entry            (some #(= user-sub (:player-sub %)) entries)
                   now                  (java.time.Instant/now)
                   reg-open             (domain/is-registration-open? state now)
                   is-organizer         (= user-sub (:created-by-sub data))
                   league               (when (:league-eid data)
                                          (domain/get-league-by-eid dependencies (:league-eid data)))
                   season               (when (:season-eid data)
                                          (domain/get-season-by-eid dependencies (:season-eid data)))
                   matches-by-phase     (domain/group-matches-by-phase raw-matches phases qualifier-count)
                   decorated-phases     (mapv decorate-bracket-phase matches-by-phase)
                   current-phase-config (get phases (:current-phase state))
                   current-phase-label  (or (phase-type-labels (:phase-type current-phase-config))
                                            (:phase-type current-phase-config))
                   phase-count          (count phases)
                   schedule             (pending-matches-schedule matches-by-phase)]
               {:tournament-state    (update state :standings decorate-standings (:qualifier-count state))
                :entries             entries
                :matches-by-phase    decorated-phases
                :schedule            schedule
                :current-phase-label current-phase-label
                :current-round-label (current-round-label decorated-phases (:eid (first schedule)))
                :phase-count         phase-count
                :single-phase?       (= 1 phase-count)
                :league              league
                :season              season
                :has-entry           has-entry
                :registration-open   reg-open
                :is-organizer        is-organizer}))))

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
      (->> (db/get-units-by-keys (:connection dependencies) all-keys)
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
      (let [rows       (db/get-subfactions-by-keys (:connection dependencies) keys)
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

(def ^:private parse-log-row-stagger-ms
  "Per-row delay between the staggered Step-2 log lines. Matches the
  cadence of the original JS-driven animation."
  280)

(defn- parse-log-rows
  "Builds the staggered Step-2 log lines (5 per game) with their inline
  CSS animation-delay so the parsing overlay reveals them at a steady
  280ms cadence."
  [game-count]
  (vec
   (map-indexed
    (fn [idx [game-num label]]
      {:label    (str/replace label "{n}" (str game-num))
       :delay-ms (* idx parse-log-row-stagger-ms)})
    (for [game-num (range 1 (inc game-count))
          label    ["Reading replay {n}"
                    "Verifying header"
                    "Detecting players & factions"
                    "Resolving draft compositions"
                    "Reading map & duration"]]
      [game-num label]))))

(defn- parse-swap-delay-ms
  "Defers the HTMX swap so the parse-log animation gets time to play
  even when the parser returns before it finishes. We hold back a few
  rows' worth of stagger so the response can land during the last leg
  of the animation rather than after it."
  [game-count]
  (max 0 (* (- (* game-count 5) 3) parse-log-row-stagger-ms)))

(defmethod integrant.core/init-key ::modal-view
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]} :path} :parameters
        session                     :ory-session
        :as                         _request}]
    (if-let [{:keys [match games]}
             (domain/get-record-context dependencies match-eid)]
      {:status 200
       :body   (render/render-component "match-record-modal.html"
                                        {:match               match
                                         :games               games
                                         :viewer-sub          (get-in session [:identity :id])
                                         :game-count          (:format match)
                                         :game-indexes        (vec (range (:format match)))
                                         :parse-log-rows      (parse-log-rows (:format match))
                                         :parse-swap-delay-ms (parse-swap-delay-ms (:format match))})}
      {:status 404
       :body   {:type :missing/resource :name "match" :id match-eid}})))

;; ─── Fragment endpoints (HTMX-driven modal flow) ──────────────────────────

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

(defmethod integrant.core/init-key ::parse-replays-fragment
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]} :path} :parameters
        multipart-params            :multipart-params
        session                     :ory-session
        :as                         _request}]
    (let [files (collect-game-files multipart-params)
          match (db/get-match-by-eid (:connection dependencies) match-eid)]
      (cond
        (nil? match)        (error-fragment "Match not found.")
        (empty? files)      (error-fragment "No replay files supplied.")
        :else
        (try
          (let [parsed       (domain/parse-replay-files dependencies (mapv :file-path files))
                key->row     (resolve-units dependencies parsed)
                level-costs  (db/get-unit-level-costs (:connection dependencies))
                enriched     (mapv #(enrich-parsed key->row level-costs %) parsed)
                faction->row (resolve-faction-keys dependencies parsed)
                viewer       (get-in session [:identity :id])
                games        (mapv (fn [game-index file parsed-map]
                                     (build-game-context match viewer game-index
                                                         (:source-name file) parsed-map
                                                         faction->row))
                                   (range)
                                   files
                                   enriched)]
            {:status  200
             :headers {"Content-Type" "text/html; charset=utf-8"}
             :body    (render/render-component "match-record-review-fragment.html"
                                               {:match match
                                                :games games})})
          (catch Exception e
            (error-fragment (str "Replay parse failed: " (.getMessage e)))))))))

(defn- collect-form-games
  "Reads back the hidden parsed-N / source-name-N / winner-sub-N fields
  from the submit form. Skips games with no winner declared (clinched
  series may leave the trailing radios blank)."
  [form-params game-count]
  (->> (range game-count)
       (keep (fn [idx]
               (let [winner      (get form-params (str "winner-sub-" idx))
                     parsed-json (get form-params (str "parsed-" idx))
                     source-name (get form-params (str "source-name-" idx))]
                 (when (and (not (str/blank? winner))
                            (not (str/blank? parsed-json)))
                   {:winner-sub  winner
                    :source-name source-name
                    :parsed      (-> parsed-json
                                     (jsonista/read-value
                                      (jsonista/object-mapper {:decode-key-fn keyword}))
                                     snake->kebab)}))))
       vec))

(defmethod integrant.core/init-key ::record-match-fragment
  [_init-key dependencies]
  (fn [{{{:keys [match-eid]} :path} :parameters
        session                     :ory-session
        form-params                 :form-params
        :as                         _request}]
    (let [match (db/get-match-by-eid (:connection dependencies) match-eid)]
      (cond
        (nil? match) (error-fragment "Match not found.")
        :else
        (let [games      (collect-form-games form-params (:format match))
              submission {:games           games
                          :uploaded-by-sub (get-in session [:identity :id])}
              result     (domain/record-match-from-parsed dependencies match-eid submission)]
          (case (:type result)
            :match-record/recorded
            (let [p1          (:player-one-sub match)
                  p2          (:player-two-sub match)
                  win-counts  (frequencies (keep :winner-sub (:games result)))
                  result-rows (mapv (fn [g]
                                      {:game-num (inc (:game-index g))
                                       :p1-sub   p1
                                       :p2-sub   p2
                                       :p1-won   (= p1 (:winner-sub g))
                                       :p2-won   (= p2 (:winner-sub g))})
                                    (:games result))]
              {:status  201
               :headers {"Content-Type"            "text/html; charset=utf-8"
                         "HX-Trigger-After-Settle" "match-recorded"}
               :body    (render/render-component "match-record-submitted-fragment.html"
                                                 {:winner-sub  (:winner-sub result)
                                                  :p1-wins     (get win-counts p1 0)
                                                  :p2-wins     (get win-counts p2 0)
                                                  :result-rows result-rows})})

            :match-record/error
            (error-fragment (:message result))))))))
