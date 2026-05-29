(ns com.devereux-henley.rts-data-access.contract
  (:require
   [com.devereux-henley.datalog.contract :as dl]
   [com.devereux-henley.rts-data-access.query.datalog.draft :as query.datalog.draft]
   [com.devereux-henley.rts-data-access.query.datalog.game :as query.datalog.game]
   [com.devereux-henley.rts-data-access.query.datalog.league :as query.datalog.league]
   [com.devereux-henley.rts-data-access.query.datalog.season :as query.datalog.season]
   [com.devereux-henley.rts-data-access.query.game :as query.game]
   [com.devereux-henley.rts-data-access.query.replay :as query.replay]
   [com.devereux-henley.rts-data-access.query.social-media :as query.social-media]
   [com.devereux-henley.rts-data-access.query.stats :as query.stats]
   [com.devereux-henley.rts-data-access.query.tournament :as query.tournament]
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.rts-data-access.schema.datalog :as schema.datalog]
   [com.devereux-henley.rts-data-access.schema.draft :as schema.draft]
   [com.devereux-henley.rts-data-access.schema.game :as schema.game]
   [com.devereux-henley.rts-data-access.schema.league :as schema.league]
   [com.devereux-henley.rts-data-access.schema.season :as schema.season]
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

;;; ─── Game DB queries ───────────────────────────────────────────────────────

(def get-draft-lock-info query.game/get-draft-lock-info)
(def draft-lock-info-schema query.game/draft-lock-info-schema)

(def get-game-modes-for-game query.game/get-game-modes-for-game)

(def item-entity schema/item-entity)

(def mount-entity schema/mount-entity)
(def get-mounts-for-unit query.game/get-mounts-for-unit)

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
(def game-modes-for-game  query.datalog.game/game-modes-for-game)
(def socials-for-game     query.datalog.game/socials-for-game)
(def mounts-for-unit      query.datalog.game/mounts-for-unit)
(def items-for-unit       query.datalog.game/items-for-unit)
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

;;; ─── Tournament DB entities ────────────────────────────────────────────────

(def tournament-entity schema/tournament-entity)
(def create-tournament-params schema/create-tournament-params)
(def tournament-state-entity schema/tournament-state-entity)
(def tournament-entry-entity schema/tournament-entry-entity)
(def match-entity schema/match-entity)
(def match-game-entity schema/match-game-entity)

(def tournament-status-enum schema/tournament-status-enum)
(def phase-type-enum schema/phase-type-enum)
(def match-status-enum schema/match-status-enum)
(def match-format-enum schema/match-format-enum)
(def bracket-type-enum schema/bracket-type-enum)
(def mark-enum schema/mark-enum)

;;; ─── Tournament DB queries ────────────────────────────────────────────────

(def get-tournament-by-eid query.tournament/get-tournament-by-eid)
(def get-tournaments-for-game query.tournament/get-tournaments-for-game)
(def get-tournaments query.tournament/get-tournaments)
(def create-tournament query.tournament/create-tournament)
(def get-tournament-state query.tournament/get-tournament-state)
(def upsert-tournament-state query.tournament/upsert-tournament-state)
(def create-entry query.tournament/create-entry)
(def delete-entry query.tournament/delete-entry)
(def get-entries-for-tournament query.tournament/get-entries-for-tournament)
(def get-entry-by-tournament-and-player query.tournament/get-entry-by-tournament-and-player)
(def create-match query.tournament/create-match)
(def get-match-by-eid query.tournament/get-match-by-eid)
(def get-matches-for-tournament query.tournament/get-matches-for-tournament)
(def get-matches query.tournament/get-matches)
(def get-matches-for-round query.tournament/get-matches-for-round)
(def update-match-result query.tournament/update-match-result)
(def create-game query.tournament/create-game)
(def get-games-for-match query.tournament/get-games-for-match)

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

(def get-faction-standings-for-game query.stats/get-faction-standings-for-game)
(def get-faction-standings-for-league query.stats/get-faction-standings-for-league)
(def get-faction-standings-for-season query.stats/get-faction-standings-for-season)

;;; ─── Social Media DB entities ──────────────────────────────────────────────

(def platform-entity schema/platform-entity)

;;; ─── Social Media DB queries ──────────────────────────────────────────────

(def get-platform-by-eid query.social-media/get-platform-by-eid)

;;; ─── Replay ────────────────────────────────────────────────────────────────

(def replay-entity        schema/replay-entity)
(def create-replay-params schema/create-replay-params)

(def get-replay-by-eid       query.replay/get-replay-by-eid)
(def create-replay           query.replay/create-replay)
(def get-units-by-keys       query.replay/get-units-by-keys)
(def get-subfactions-by-keys query.replay/get-subfactions-by-keys)

;;; ─── Function schemas ─────────────────────────────────────────────────────
;;;
;;; All `=>*` declarations live here so adding/auditing contracts is one
;;; scroll, not a hunt through the re-exports above.

;; Game datalog

(schema.contract/=>* games query.datalog.game/games
                     [:=> [:cat dl/conn-schema]
                      [:sequential schema.game/game-schema]])

(schema.contract/=>* game-by-eid query.datalog.game/game-by-eid
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe schema.game/game-schema]])

(schema.contract/=>* factions query.datalog.game/factions
                     [:=> [:cat dl/conn-schema]
                      [:sequential schema.game/faction-schema]])

(schema.contract/=>* factions-for-game query.datalog.game/factions-for-game
                     [:=> [:cat dl/conn-schema :uuid]
                      [:sequential schema.game/faction-schema]])

(schema.contract/=>* faction-by-eid query.datalog.game/faction-by-eid
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe schema.game/faction-schema]])

(schema.contract/=>* units query.datalog.game/units
                     [:=> [:cat dl/conn-schema]
                      [:sequential schema.game/unit-summary-schema]])

(schema.contract/=>* units-for-game query.datalog.game/units-for-game
                     [:=> [:cat dl/conn-schema :uuid]
                      [:sequential schema.game/unit-summary-schema]])

(schema.contract/=>* units-for-faction query.datalog.game/units-for-faction
                     [:=> [:cat dl/conn-schema :uuid]
                      [:sequential schema.game/unit-summary-schema]])

(schema.contract/=>* unit-by-eid query.datalog.game/unit-by-eid
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe schema.game/unit-detail-schema]])

(schema.contract/=>* game-mode-by-eid query.datalog.game/game-mode-by-eid
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe schema.game/game-mode-schema]])

(schema.contract/=>* game-modes-for-game query.datalog.game/game-modes-for-game
                     [:=> [:cat dl/conn-schema :uuid]
                      [:sequential schema.game/game-mode-schema]])

(schema.contract/=>* socials-for-game query.datalog.game/socials-for-game
                     [:=> [:cat dl/conn-schema :uuid]
                      [:sequential schema.game/social-link-schema]])

(schema.contract/=>* mounts-for-unit query.datalog.game/mounts-for-unit
                     [:=> [:cat dl/conn-schema :uuid]
                      [:sequential schema.game/mount-row-schema]])

(schema.contract/=>* items-for-unit query.datalog.game/items-for-unit
                     [:=> [:cat dl/conn-schema :uuid]
                      [:sequential schema.game/item-row-schema]])

(schema.contract/=>* spells-by-keys query.datalog.game/spells-by-keys
                     [:=> [:cat dl/conn-schema [:sequential :string]]
                      [:maybe [:map-of :string schema.game/spell-row-schema]]])

(schema.contract/=>* spells-for-lore query.datalog.game/spells-for-lore
                     [:=> [:cat dl/conn-schema :string]
                      [:sequential schema.game/spell-row-schema]])

(schema.contract/=>* abilities-by-keys query.datalog.game/abilities-by-keys
                     [:=> [:cat dl/conn-schema [:sequential :string]]
                      [:maybe [:map-of :string schema.game/ability-row-schema]]])

(schema.contract/=>* unit-level-costs query.datalog.game/unit-level-costs
                     [:=> [:cat dl/conn-schema]
                      [:map-of :int schema.game/unit-level-cost-schema]])

(schema.contract/=>* family-variants-by-eid query.datalog.game/family-variants-by-eid
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe [:sequential schema.game/family-variant-schema]]])

;; Draft datalog

(schema.contract/=>* draft-by-eid query.datalog.draft/draft-by-eid
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe schema.draft/draft-result-schema]])

(schema.contract/=>* drafts-for-player query.datalog.draft/drafts-for-player
                     [:=> [:cat dl/conn-schema :string]
                      [:sequential schema.draft/draft-result-schema]])

(schema.contract/=>* drafts-for-player-by-game query.datalog.draft/drafts-for-player-by-game
                     [:=> [:cat dl/conn-schema :string :uuid]
                      [:sequential schema.draft/draft-result-schema]])

(schema.contract/=>* draft-state-by-eid query.datalog.draft/draft-state-by-eid
                     [:=> [:cat dl/conn-schema :uuid] schema.draft/draft-state-schema])

(schema.contract/=>* draft-entry-by-eid query.datalog.draft/draft-entry-by-eid
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe schema.draft/draft-entry-schema]])

(schema.contract/=>* draft-entry-section-and-ordinal query.datalog.draft/draft-entry-section-and-ordinal
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe [:map
                               [:section [:enum :main :reinforcements]]
                               [:ordinal :int]]]])

(schema.contract/=>* create-draft! query.datalog.draft/create-draft!
                     [:=> [:cat dl/conn-schema schema.draft/create-spec-schema]
                      schema.draft/draft-result-schema])

(schema.contract/=>* update-draft-name! query.datalog.draft/update-draft-name!
                     [:=> [:cat dl/conn-schema :uuid [:maybe :string]]
                      schema.draft/draft-result-schema])

(schema.contract/=>* add-entry! query.datalog.draft/add-entry!
                     [:=> [:cat dl/conn-schema :uuid schema.draft/entry-add-spec-schema]
                      dl/tx-report-schema])

(schema.contract/=>* add-entries! query.datalog.draft/add-entries!
                     [:=> [:cat dl/conn-schema :uuid
                           [:sequential schema.draft/entry-batch-spec-schema]]
                      dl/tx-report-schema])

(schema.contract/=>* remove-entry! query.datalog.draft/remove-entry!
                     [:=> [:cat dl/conn-schema :uuid :uuid]
                      dl/tx-report-schema])

(schema.contract/=>* update-entry! query.datalog.draft/update-entry!
                     [:=> [:cat dl/conn-schema :uuid :uuid schema.draft/entry-update-attrs-schema]
                      dl/tx-report-schema])

;; League datalog

(schema.contract/=>* league-by-eid query.datalog.league/league-by-eid
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe schema.league/league-result-schema]])

(schema.contract/=>* leagues query.datalog.league/leagues
                     [:=> [:cat dl/conn-schema]
                      [:sequential schema.league/league-result-schema]])

(schema.contract/=>* leagues-for-game query.datalog.league/leagues-for-game
                     [:=> [:cat dl/conn-schema :uuid]
                      [:sequential schema.league/league-result-schema]])

(schema.contract/=>* create-league! query.datalog.league/create-league!
                     [:=> [:cat dl/conn-schema schema.league/create-spec-schema]
                      schema.league/league-result-schema])

;; Season datalog

(schema.contract/=>* season-by-eid query.datalog.season/season-by-eid
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe schema.season/season-result-schema]])

(schema.contract/=>* seasons query.datalog.season/seasons
                     [:=> [:cat dl/conn-schema]
                      [:sequential schema.season/season-result-schema]])

(schema.contract/=>* seasons-for-league query.datalog.season/seasons-for-league
                     [:=> [:cat dl/conn-schema :uuid]
                      [:sequential schema.season/season-result-schema]])

(schema.contract/=>* current-season-for-league query.datalog.season/current-season-for-league
                     [:=> [:cat dl/conn-schema :uuid]
                      [:maybe schema.season/season-result-schema]])

(schema.contract/=>* max-ordinal-for-league query.datalog.season/max-ordinal-for-league
                     [:=> [:cat dl/conn-schema :uuid]
                      schema.season/max-ordinal-schema])

(schema.contract/=>* create-season! query.datalog.season/create-season!
                     [:=> [:cat dl/conn-schema schema.season/create-spec-schema]
                      schema.season/season-result-schema])
