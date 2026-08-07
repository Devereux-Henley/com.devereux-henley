(ns com.devereux-henley.rts-data-access.contract
  (:require
   [com.devereux-henley.rts-data-access.query.datalog.dispute :as query.datalog.dispute]
   [com.devereux-henley.rts-data-access.query.datalog.draft :as query.datalog.draft]
   [com.devereux-henley.rts-data-access.query.datalog.game :as query.datalog.game]
   [com.devereux-henley.rts-data-access.query.datalog.league :as query.datalog.league]
   [com.devereux-henley.rts-data-access.query.datalog.replay :as query.datalog.replay]
   [com.devereux-henley.rts-data-access.query.datalog.season :as query.datalog.season]
   [com.devereux-henley.rts-data-access.query.datalog.social-media :as query.datalog.social-media]
   [com.devereux-henley.rts-data-access.query.datalog.tournament :as query.datalog.tournament]
   [com.devereux-henley.rts-data-access.query.datalog.stats :as query.datalog.stats]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.rts-data-access.schema.datalog :as schema.datalog]
   [com.devereux-henley.rts-data-access.schema.dispute :as schema.dispute]
   [com.devereux-henley.rts-data-access.schema.draft :as schema.draft]
   [com.devereux-henley.rts-data-access.schema.game :as schema.game]
   [com.devereux-henley.rts-data-access.schema.league :as schema.league]
   [com.devereux-henley.rts-data-access.schema.replay :as schema.replay]
   [com.devereux-henley.rts-data-access.schema.season :as schema.season]
   [com.devereux-henley.rts-data-access.schema.social-media :as schema.social-media]
   [com.devereux-henley.schema.contract :as schema.contract]))

;;; ─── Datalog schema-as-code ────────────────────────────────────────────────

(def datalog-schema schema.datalog/schema)

;;; ─── Game DB entities ──────────────────────────────────────────────────────

(def game-mode-entity schema/game-mode-entity)

(def game-entity schema/game-entity)

(def faction-entity schema/faction-entity)

(def game-social-link-entity schema/game-social-link-entity)

(def unit-type-entity schema/unit-type-entity)

(def unit-category-entity schema/unit-category-entity)

(def unit-entity schema/unit-entity)

;;; ─── Draft-lock query ──────────────────────────────────────────────────────
;;
;; Reports whether a draft is locked by a recorded game. Currently returns
;; a stable empty (never-locked) result.

(def get-draft-lock-info query.datalog.draft/draft-lock-info)
(def draft-lock-info-schema
  "Shape returned by `get-draft-lock-info` — the earliest tournament match
  referencing a draft, or nil when it's still editable."
  (schema.contract/to-schema
   [:map
    [:match-eid       :uuid]
    [:tournament-eid  :uuid]
    [:tournament-name :string]]))

(def item-entity schema/item-entity)

(def mount-entity schema/mount-entity)

(def lore-entity schema/lore-entity)

(def unit-level-cost-entity schema/unit-level-cost-entity)

(def unit-statistics-raw-schema schema/unit-statistics-raw-schema)
(def unit-statistics-transformer schema/unit-statistics-transformer)

;;; ─── Game Datalog queries (per-template) ───────────────────────────────────

(def games                query.datalog.game/games)
(def game-by-eid          query.datalog.game/game-by-eid)
(def factions             query.datalog.game/factions)
(def factions-for-game    query.datalog.game/factions-for-game)
(def faction-by-eid       query.datalog.game/faction-by-eid)
(def units                query.datalog.game/units)
(def units-for-game       query.datalog.game/units-for-game)
(def units-for-faction    query.datalog.game/units-for-faction)
(def unit-by-eid          query.datalog.game/unit-by-eid)
(def units-by-keys        query.datalog.game/units-by-keys)
(def subfactions-by-keys  query.datalog.game/subfactions-by-keys)
(def game-modes-for-game  query.datalog.game/game-modes-for-game)
(def socials-for-game     query.datalog.game/socials-for-game)
(def mounts-for-unit      query.datalog.game/mounts-for-unit)
(def items-for-unit       query.datalog.game/items-for-unit)
(def items-by-ability-keys query.datalog.game/items-by-ability-keys)
(def spells-by-keys       query.datalog.game/spells-by-keys)
(def spells-for-lore      query.datalog.game/spells-for-lore)
(def abilities-by-keys    query.datalog.game/abilities-by-keys)
(def game-mode-by-eid     query.datalog.game/game-mode-by-eid)
(def unit-level-costs     query.datalog.game/unit-level-costs)
(def family-variants-by-eid query.datalog.game/family-variants-by-eid)

;;; ─── Draft Datalog queries + mutations ────────────────────────────────────

(def draft-entry-schema              schema.draft/draft-entry-schema)
(def draft-state-schema              schema.draft/draft-state-schema)

(def draft-by-eid                    query.datalog.draft/draft-by-eid)
(def drafts-for-player               query.datalog.draft/drafts-for-player)
(def drafts-for-player-by-game       query.datalog.draft/drafts-for-player-by-game)
(def draft-state-by-eid              query.datalog.draft/draft-state-by-eid)
(def draft-entry-by-eid              query.datalog.draft/draft-entry-by-eid)
(def draft-entry-section-and-ordinal query.datalog.draft/draft-entry-section-and-ordinal)
(def create-draft!                   query.datalog.draft/create-draft!)
(def update-draft-name!              query.datalog.draft/update-draft-name!)
(def add-entry!                      query.datalog.draft/add-entry!)
(def add-entries!                    query.datalog.draft/add-entries!)
(def remove-entry!                   query.datalog.draft/remove-entry!)
(def update-entry!                   query.datalog.draft/update-entry!)

;;; ─── Tournament / match enums ──────────────────────────────────────────────

(def tournament-status-enum schema/tournament-status-enum)
(def phase-type-enum schema/phase-type-enum)
(def match-status-enum schema/match-status-enum)
(def match-format-enum schema/match-format-enum)
(def bracket-type-enum schema/bracket-type-enum)
(def mark-enum schema/mark-enum)

(def dispute-kind-enum schema/dispute-kind-enum)
(def dispute-priority-enum schema/dispute-priority-enum)
(def dispute-status-enum schema/dispute-status-enum)

;;; ─── Tournament Datalog queries + mutations ────────────────────────────────

(def tournament-by-eid               query.datalog.tournament/tournament-by-eid)
(def tournaments-for-game            query.datalog.tournament/tournaments-for-game)
(def tournaments                     query.datalog.tournament/tournaments)
(def phases-for-tournament           query.datalog.tournament/phases-for-tournament)
(def create-tournament!              query.datalog.tournament/create-tournament!)
(def update-tournament!              query.datalog.tournament/update-tournament!)
(def set-phases!                     query.datalog.tournament/set-phases!)
(def entries-for-tournament          query.datalog.tournament/entries-for-tournament)
(def entry-by-tournament-and-player  query.datalog.tournament/entry-by-tournament-and-player)
(def create-entry!                   query.datalog.tournament/create-entry!)
(def delete-entry!                   query.datalog.tournament/delete-entry!)
(def match-by-eid                    query.datalog.tournament/match-by-eid)
(def matches-for-tournament          query.datalog.tournament/matches-for-tournament)
(def matches                         query.datalog.tournament/matches)
(def matches-for-round               query.datalog.tournament/matches-for-round)
(def create-match!                   query.datalog.tournament/create-match!)
(def update-match-result!            query.datalog.tournament/update-match-result!)
(def open-match-check-in!            query.datalog.tournament/open-match-check-in!)
(def record-match-check-in!          query.datalog.tournament/record-match-check-in!)
(def set-match-lobby-code!           query.datalog.tournament/set-match-lobby-code!)
(def create-game!                    query.datalog.tournament/create-game!)
(def games-for-match                 query.datalog.tournament/games-for-match)
(def match-game-by-eid               query.datalog.tournament/match-game-by-eid)
(def confirm-game!                   query.datalog.tournament/confirm-game!)
(def dispute-game!                   query.datalog.tournament/dispute-game!)

;;; ─── Stats DB entities ─────────────────────────────────────────────────────

(def faction-standings-row-entity schema/faction-standings-row-entity)

;;; ─── League Datalog queries + mutations ───────────────────────────────────

(def league-by-eid       query.datalog.league/league-by-eid)
(def leagues             query.datalog.league/leagues)
(def leagues-for-game    query.datalog.league/leagues-for-game)
(def create-league!      query.datalog.league/create-league!)

;;; ─── Season Datalog queries + mutations ───────────────────────────────────

(def season-by-eid              query.datalog.season/season-by-eid)
(def seasons                    query.datalog.season/seasons)
(def seasons-for-league         query.datalog.season/seasons-for-league)
(def current-season-for-league  query.datalog.season/current-season-for-league)
(def max-ordinal-for-league     query.datalog.season/max-ordinal-for-league)
(def create-season!             query.datalog.season/create-season!)

;;; ─── Stats DB queries ──────────────────────────────────────────────────────

(def get-faction-standings-for-game query.datalog.stats/faction-standings-for-game)
(def get-faction-standings-for-league query.datalog.stats/faction-standings-for-league)
(def get-faction-standings-for-season query.datalog.stats/faction-standings-for-season)

;;; ─── Social Media Datalog queries ──────────────────────────────────────────

(def platform-by-eid query.datalog.social-media/platform-by-eid)

;;; ─── Replay Datalog queries + mutations ───────────────────────────────────

(def replay-by-eid  query.datalog.replay/replay-by-eid)
(def create-replay! query.datalog.replay/create-replay!)

;;; ─── Dispute Datalog queries + mutations ───────────────────────────────────

(def dispute-by-eid                   query.datalog.dispute/dispute-by-eid)
(def open-disputes-for-tournament     query.datalog.dispute/open-disputes-for-tournament)
(def open-dispute-count-for-tournament query.datalog.dispute/open-dispute-count-for-tournament)
(def create-dispute!                  query.datalog.dispute/create-dispute!)
(def resolve-dispute!                 query.datalog.dispute/resolve-dispute!)
(def dismiss-dispute!                 query.datalog.dispute/dismiss-dispute!)

;;; ─── Function schemas ─────────────────────────────────────────────────────
;;;
;;; All `=>*` declarations live here so adding/auditing contracts is one
;;; scroll, not a hunt through the re-exports above.

;; Opaque Datalevin connection — pre-validating it would couple the data-access
;; contracts to datalevin internals for no gain.
(def ^:private conn-schema :any)

;; Datalevin transact!'s return value, passed through as-is.
(def ^:private tx-report-schema :any)

;; Game datalog

(schema.contract/=>* games query.datalog.game/games
                     [:=> [:cat conn-schema]
                      [:sequential schema.game/game-schema]])

(schema.contract/=>* game-by-eid query.datalog.game/game-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.game/game-schema]])

(schema.contract/=>* factions query.datalog.game/factions
                     [:=> [:cat conn-schema]
                      [:sequential schema.game/faction-schema]])

(schema.contract/=>* factions-for-game query.datalog.game/factions-for-game
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.game/faction-schema]])

(schema.contract/=>* faction-by-eid query.datalog.game/faction-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.game/faction-schema]])

(schema.contract/=>* units query.datalog.game/units
                     [:=> [:cat conn-schema]
                      [:sequential schema.game/unit-summary-schema]])

(schema.contract/=>* units-for-game query.datalog.game/units-for-game
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.game/unit-summary-schema]])

(schema.contract/=>* units-for-faction query.datalog.game/units-for-faction
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.game/unit-summary-schema]])

(schema.contract/=>* unit-by-eid query.datalog.game/unit-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.game/unit-detail-schema]])

(schema.contract/=>* game-mode-by-eid query.datalog.game/game-mode-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.game/game-mode-schema]])

(schema.contract/=>* game-modes-for-game query.datalog.game/game-modes-for-game
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.game/game-mode-schema]])

(schema.contract/=>* socials-for-game query.datalog.game/socials-for-game
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.game/social-link-schema]])

(schema.contract/=>* mounts-for-unit query.datalog.game/mounts-for-unit
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.game/mount-row-schema]])

(schema.contract/=>* items-for-unit query.datalog.game/items-for-unit
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.game/item-row-schema]])

(schema.contract/=>* items-by-ability-keys query.datalog.game/items-by-ability-keys
                     [:=> [:cat conn-schema [:sequential :string]]
                      [:maybe [:map-of :string schema.game/item-row-schema]]])

(schema.contract/=>* spells-by-keys query.datalog.game/spells-by-keys
                     [:=> [:cat conn-schema [:sequential :string]]
                      [:maybe [:map-of :string schema.game/spell-row-schema]]])

(schema.contract/=>* spells-for-lore query.datalog.game/spells-for-lore
                     [:=> [:cat conn-schema :string]
                      [:sequential schema.game/spell-row-schema]])

(schema.contract/=>* abilities-by-keys query.datalog.game/abilities-by-keys
                     [:=> [:cat conn-schema [:sequential :string]]
                      [:maybe [:map-of :string schema.game/ability-row-schema]]])

(schema.contract/=>* unit-level-costs query.datalog.game/unit-level-costs
                     [:=> [:cat conn-schema]
                      [:map-of :int schema.game/unit-level-cost-schema]])

(schema.contract/=>* family-variants-by-eid query.datalog.game/family-variants-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe [:sequential schema.game/family-variant-schema]]])

;; Draft datalog

(schema.contract/=>* draft-by-eid query.datalog.draft/draft-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.draft/draft-result-schema]])

(schema.contract/=>* get-draft-lock-info query.datalog.draft/draft-lock-info
                     [:=> [:cat conn-schema :uuid]
                      [:maybe draft-lock-info-schema]])

(schema.contract/=>* get-faction-standings-for-game query.datalog.stats/faction-standings-for-game
                     [:=> [:cat conn-schema :uuid]
                      [:sequential faction-standings-row-entity]])

(schema.contract/=>* get-faction-standings-for-league query.datalog.stats/faction-standings-for-league
                     [:=> [:cat conn-schema :uuid]
                      [:sequential faction-standings-row-entity]])

(schema.contract/=>* get-faction-standings-for-season query.datalog.stats/faction-standings-for-season
                     [:=> [:cat conn-schema :uuid]
                      [:sequential faction-standings-row-entity]])

(schema.contract/=>* drafts-for-player query.datalog.draft/drafts-for-player
                     [:=> [:cat conn-schema :string]
                      [:sequential schema.draft/draft-result-schema]])

(schema.contract/=>* drafts-for-player-by-game query.datalog.draft/drafts-for-player-by-game
                     [:=> [:cat conn-schema :string :uuid]
                      [:sequential schema.draft/draft-result-schema]])

(schema.contract/=>* draft-state-by-eid query.datalog.draft/draft-state-by-eid
                     [:=> [:cat conn-schema :uuid] schema.draft/draft-state-schema])

(schema.contract/=>* draft-entry-by-eid query.datalog.draft/draft-entry-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.draft/draft-entry-schema]])

(schema.contract/=>* draft-entry-section-and-ordinal query.datalog.draft/draft-entry-section-and-ordinal
                     [:=> [:cat conn-schema :uuid]
                      [:maybe [:map
                               [:section [:enum :main :reinforcements]]
                               [:ordinal :int]]]])

(schema.contract/=>* create-draft! query.datalog.draft/create-draft!
                     [:=> [:cat conn-schema schema.draft/create-spec-schema]
                      schema.draft/draft-result-schema])

(schema.contract/=>* update-draft-name! query.datalog.draft/update-draft-name!
                     [:=> [:cat conn-schema :uuid [:maybe :string]]
                      schema.draft/draft-result-schema])

(schema.contract/=>* add-entry! query.datalog.draft/add-entry!
                     [:=> [:cat conn-schema :uuid schema.draft/entry-add-spec-schema]
                      tx-report-schema])

(schema.contract/=>* add-entries! query.datalog.draft/add-entries!
                     [:=> [:cat conn-schema :uuid
                           [:sequential schema.draft/entry-batch-spec-schema]]
                      tx-report-schema])

(schema.contract/=>* remove-entry! query.datalog.draft/remove-entry!
                     [:=> [:cat conn-schema :uuid :uuid]
                      tx-report-schema])

(schema.contract/=>* update-entry! query.datalog.draft/update-entry!
                     [:=> [:cat conn-schema :uuid :uuid schema.draft/entry-update-attrs-schema]
                      tx-report-schema])

;; League datalog

(schema.contract/=>* league-by-eid query.datalog.league/league-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.league/league-result-schema]])

(schema.contract/=>* leagues query.datalog.league/leagues
                     [:=> [:cat conn-schema]
                      [:sequential schema.league/league-result-schema]])

(schema.contract/=>* leagues-for-game query.datalog.league/leagues-for-game
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.league/league-result-schema]])

(schema.contract/=>* create-league! query.datalog.league/create-league!
                     [:=> [:cat conn-schema schema.league/create-spec-schema]
                      schema.league/league-result-schema])

;; Season datalog

(schema.contract/=>* season-by-eid query.datalog.season/season-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.season/season-result-schema]])

(schema.contract/=>* seasons query.datalog.season/seasons
                     [:=> [:cat conn-schema]
                      [:sequential schema.season/season-result-schema]])

(schema.contract/=>* seasons-for-league query.datalog.season/seasons-for-league
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.season/season-result-schema]])

(schema.contract/=>* current-season-for-league query.datalog.season/current-season-for-league
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.season/season-result-schema]])

(schema.contract/=>* max-ordinal-for-league query.datalog.season/max-ordinal-for-league
                     [:=> [:cat conn-schema :uuid]
                      schema.season/max-ordinal-schema])

(schema.contract/=>* create-season! query.datalog.season/create-season!
                     [:=> [:cat conn-schema schema.season/create-spec-schema]
                      schema.season/season-result-schema])

;; Social-media datalog

(schema.contract/=>* platform-by-eid query.datalog.social-media/platform-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.social-media/platform-result-schema]])

;; Replay datalog

(schema.contract/=>* replay-by-eid query.datalog.replay/replay-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.replay/replay-result-schema]])

(schema.contract/=>* create-replay! query.datalog.replay/create-replay!
                     [:=> [:cat conn-schema schema.replay/create-spec-schema]
                      schema.replay/replay-result-schema])

;; Dispute datalog

(schema.contract/=>* dispute-by-eid query.datalog.dispute/dispute-by-eid
                     [:=> [:cat conn-schema :uuid]
                      [:maybe schema.dispute/dispute-result-schema]])

(schema.contract/=>* open-disputes-for-tournament query.datalog.dispute/open-disputes-for-tournament
                     [:=> [:cat conn-schema :uuid]
                      [:sequential schema.dispute/dispute-result-schema]])

(schema.contract/=>* open-dispute-count-for-tournament query.datalog.dispute/open-dispute-count-for-tournament
                     [:=> [:cat conn-schema :uuid]
                      [:int {:min 0}]])

(schema.contract/=>* create-dispute! query.datalog.dispute/create-dispute!
                     [:=> [:cat conn-schema schema.dispute/open-spec-schema]
                      schema.dispute/dispute-result-schema])

(schema.contract/=>* resolve-dispute! query.datalog.dispute/resolve-dispute!
                     [:=> [:cat conn-schema :uuid schema.dispute/resolve-spec-schema]
                      schema.dispute/dispute-result-schema])

(schema.contract/=>* dismiss-dispute! query.datalog.dispute/dismiss-dispute!
                     [:=> [:cat conn-schema :uuid]
                      schema.dispute/dispute-result-schema])
