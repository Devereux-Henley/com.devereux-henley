(ns com.devereux-henley.rts-web.web.tournament.share
  "Tournament data-assembly helpers shared between the /api handlers in
  `web.tournament.api` and the /view + /components fragment handlers in
  `web.tournament.view`. Anything that touches the domain layer to build
  a presentation-ready map belongs here; anything that maps that map
  onto a response (status + body shape, template render) belongs in the
  caller."
  (:require
   [com.devereux-henley.rts-domain.contract :as domain]))

(defn get-tournament-by-eid
  "Fetches a tournament, returning a `:missing/resource` marker map when
  the domain layer can't find it. Lets the caller decide between a 404
  response, a rendered missing-page, or surfacing the marker in an
  embedded resource."
  [dependencies eid]
  (or (domain/get-tournament-by-eid dependencies eid)
      {:type :missing/resource :name "tournament" :id eid}))

(defn attach-lineups-to-matches
  "Walks each round-bucket inside a phase-group and replaces every match
  slot with one carrying `:lineups` — the per-game pair of draft eids
  recorded against `match_game`. Drafts are auto-created on match-record
  submit, so completed matches end up with one lineup row per game."
  [phase-group lineups-by-match-eid]
  (let [decorate-match  (fn [m]
                          (if-let [lineups (get lineups-by-match-eid (:eid m))]
                            (assoc m :lineups lineups)
                            m))
        decorate-round  (fn [r] (update r :matches #(mapv decorate-match %)))
        decorate-bucket #(when (seq %) (mapv decorate-round %))]
    (cond-> phase-group
      (:rounds phase-group)          (update :rounds          decorate-bucket)
      (:winners-bracket phase-group) (update :winners-bracket  decorate-bucket)
      (:losers-bracket phase-group)  (update :losers-bracket   decorate-bucket)
      (:grand-final phase-group)     (update :grand-final      decorate-bucket))))

(defn build-phase-context
  "Gathers the phase-group, tournament state, and parent game-eid for one
  phase. Returns nil when no phase with `phase-index` exists. Shared by
  the /api handler (text/html resource view) and the /components
  fragment view (htmx panel)."
  [dependencies tournament-eid phase-index]
  (let [state           (domain/get-tournament-state dependencies tournament-eid)
        phases          (:phases state)
        raw-matches     (domain/get-matches-for-tournament dependencies tournament-eid)
        qualifier-count (or (:qualifier-count state) (count (:standings state)))
        grouped         (domain/group-matches-by-phase raw-matches phases qualifier-count)
        phase-group-raw (first (filter #(= phase-index (:phase %)) grouped))
        real-matches    (filter :eid raw-matches)
        lineups         (into {}
                              (map (fn [m]
                                     [(:eid m)
                                      (domain/get-games-for-match dependencies (:eid m))]))
                              real-matches)]
    (when phase-group-raw
      {:tournament-state state
       :phase-group      (attach-lineups-to-matches phase-group-raw lineups)
       :game-eid         (:game-eid (get-tournament-by-eid dependencies tournament-eid))})))
