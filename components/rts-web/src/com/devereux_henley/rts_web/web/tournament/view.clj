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
   [com.devereux-henley.rts-web.web.tournament.api :as web.tournament.api]
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
       :body   (render/render "competitive.html"
                              (assoc (web.view/base-context request)
                                     :tournaments enriched-tournaments
                                     :leagues enriched-leagues))})))

(defmethod integrant.core/init-key ::create-tournament-view
  [_init-key dependencies]
  (fn [request]
    (let [game-eid (:game-eid (:game-context request))
          leagues  (domain/get-leagues-for-game dependencies game-eid)]
      {:status 200
       :body   (render/render "create-tournament.html"
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
       :body    (render/render "tournament-phase-row.html"
                               (assoc (web.view/base-context request) :tournament-eid eid))})))

(defmethod integrant.core/init-key ::tournament-view
  [_init-key dependencies]
  (partial web.view/standard-entity-view-handler
           (fn [eid] (web.tournament.api/get-tournament-by-eid dependencies eid))
           "tournament-index.html"
           (fn [data request]
             (let [tournament-eid        (:eid data)
                   state                 (domain/get-tournament-state dependencies tournament-eid)
                   entries               (domain/get-entries dependencies tournament-eid)
                   raw-matches           (domain/get-matches-for-tournament dependencies tournament-eid)
                   phases                (:phases state)
                   qualifier-count       (or (:qualifier-count state) (count (:standings state)))
                   user-sub              (get-in request [:ory-session :identity :id])
                   has-entry             (some #(= user-sub (:player-sub %)) entries)
                   now                   (java.time.Instant/now)
                   reg-open              (domain/is-registration-open? state now)
                   is-organizer          (= user-sub (:created-by-sub data))
                   organizer-has-actions (and is-organizer
                                              (contains? #{"registration" "active"} (:status state)))
                   league                (when (:league-eid data)
                                           (domain/get-league-by-eid dependencies (:league-eid data)))
                   season                (when (:season-eid data)
                                           (domain/get-season-by-eid dependencies (:season-eid data)))]
               {:tournament-state      state
                :entries               entries
                :matches-by-phase      (domain/group-matches-by-phase raw-matches phases qualifier-count)
                :league                league
                :season                season
                :has-entry             has-entry
                :registration-open     reg-open
                :is-organizer          is-organizer
                :organizer-has-actions organizer-has-actions}))))

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
  "Adds resolved unit data (name, cost, category) to a unit map when its
  engine key has a matching DB row.  Leaves the map unchanged otherwise so
  the client can fall back to the raw key."
  [key->row {:keys [key] :as unit}]
  (if-let [row (resolve-key key->row key)]
    (assoc unit
           :name               (:name row)
           :cost               (:cost row)
           :unit-category-name (:unit-category-name row)
           :unit-type-name     (:unit-type-name row)
           :unit-eid           (:eid row))
    unit))

(defn- enrich-parsed
  "Threads `enrich-unit` through every unit in the parsed structure."
  [key->row parsed]
  (update parsed :alliances
          (fn [alliances]
            (mapv (fn [alliance]
                    (update alliance :armies
                            (fn [armies]
                              (mapv (fn [army]
                                      (update army :units
                                              (fn [units]
                                                (mapv (partial enrich-unit key->row) units))))
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
       :body   (render/render "match-record-modal.html"
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
  (mapv (fn [{:keys [key name cost unit-category-name unit-type-name unit-eid]}]
          (let [enriched? (some? name)
                ;; FALLBACK (#49): unresolved parser key shows the raw engine key.
                display   (if enriched? name key)
                ;; FALLBACK (#49): missing category data falls through to type, then em-dash.
                category  (or unit-category-name unit-type-name "—")
                is-lord?  (= "lord" (some-> unit-category-name str/lower-case))]
            {:key      key
             :unit-eid unit-eid
             :display  display
             :cost     cost
             :is-lord  is-lord?
             ;; FALLBACK (#49): tooltip omits cost when the DB row is missing.
             :tooltip  (if cost
                         (str category " · " cost " pts")
                         category)}))
        (:units army)))

(defn- section-totals
  "Per-section count/cost pair mirroring `alliance-totals` at section
  scope: cost in pts when every unit in the section is enriched, otherwise
  the section's army-level :force-value sum (model count).

  The model-count branch is a FALLBACK (#49) for partial enrichment — once
  #45 guarantees every parser key matches a DB unit row, this collapses
  to just the pts branch."
  [armies units]
  (let [enriched-count (count (filter :cost units))]
    (if (and (pos? enriched-count) (= enriched-count (count units)))
      {:section-num  (reduce + 0 (keep :cost units))
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
  When every unit is enriched, reports total point cost; otherwise falls
  back to model count so the header isn't blank."
  [alliance units]
  (let [enriched-count (count (filter :cost units))]
    (if (and (pos? enriched-count) (= enriched-count (count units)))
      {:total-num  (reduce + 0 (keep :cost units))
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
                enriched     (mapv #(enrich-parsed key->row %) parsed)
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
             :body    (render/render "match-record-review-fragment.html"
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
               :body    (render/render "match-record-submitted-fragment.html"
                                       {:winner-sub  (:winner-sub result)
                                        :p1-wins     (get win-counts p1 0)
                                        :p2-wins     (get win-counts p2 0)
                                        :result-rows result-rows})})

            :match-record/error
            (error-fragment (:message result))))))))
