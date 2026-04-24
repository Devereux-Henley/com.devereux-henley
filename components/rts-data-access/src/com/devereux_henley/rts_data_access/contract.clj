(ns com.devereux-henley.rts-data-access.contract
  (:require
   [com.devereux-henley.rts-data-access.query.game :as query.game]
   [com.devereux-henley.rts-data-access.query.league :as query.league]
   [com.devereux-henley.rts-data-access.query.season :as query.season]
   [com.devereux-henley.rts-data-access.query.social-media :as query.social-media]
   [com.devereux-henley.rts-data-access.query.stats :as query.stats]
   [com.devereux-henley.rts-data-access.query.tournament :as query.tournament]
   [com.devereux-henley.rts-data-access.schema :as schema]))

;;; ─── Game DB entities ──────────────────────────────────────────────────────

(def create-draft-params schema/create-draft-params)

(def draft-entity schema/draft-entity)

(def game-mode-entity schema/game-mode-entity)

(def game-entity schema/game-entity)

(def faction-entity schema/faction-entity)

(def game-social-link-entity schema/game-social-link-entity)

(def unit-type-entity schema/unit-type-entity)

(def unit-category-entity schema/unit-category-entity)

(def unit-entity schema/unit-entity)

;;; ─── Game DB queries ───────────────────────────────────────────────────────

(def get-draft-by-eid query.game/get-draft-by-eid)

(def get-drafts-for-player query.game/get-drafts-for-player)

(def get-drafts-for-player-by-game query.game/get-drafts-for-player-by-game)

(def create-draft query.game/create-draft)
(def update-draft query.game/update-draft)

(def get-game-mode-by-eid query.game/get-game-mode-by-eid)

(def get-game-modes-for-game query.game/get-game-modes-for-game)

(def get-game-by-eid query.game/get-game-by-eid)

(def get-games query.game/get-games)

(def get-faction-by-eid query.game/get-faction-by-eid)

(def get-factions-for-game query.game/get-factions-for-game)

(def get-socials-for-game query.game/get-socials-for-game)

(def get-game-social-link-by-eid query.game/get-game-social-link-by-eid)

(def get-unit-type-by-eid query.game/get-unit-type-by-eid)

(def get-unit-types-for-game query.game/get-unit-types-for-game)

(def get-unit-category-by-eid query.game/get-unit-category-by-eid)

(def get-unit-categories-for-game query.game/get-unit-categories-for-game)

(def get-unit-by-eid query.game/get-unit-by-eid)

(def get-units-for-game query.game/get-units-for-game)

(def get-units-for-faction query.game/get-units-for-faction)

(def get-spells-by-keys query.game/get-spells-by-keys)
(def get-abilities-by-keys query.game/get-abilities-by-keys)

(def item-entity schema/item-entity)
(def get-items-for-unit query.game/get-items-for-unit)

(def mount-entity schema/mount-entity)
(def get-mounts-for-unit query.game/get-mounts-for-unit)
(def get-mount-by-key query.game/get-mount-by-key)

(def lore-entity schema/lore-entity)
(def get-lores-for-unit query.game/get-lores-for-unit)
(def get-spells-for-lore query.game/get-spells-for-lore)

(def draft-state-entity schema/draft-state-entity)
(def get-draft-state-by-draft query.game/get-draft-state-by-draft)
(def upsert-draft-state query.game/upsert-draft-state)

(def unit-statistics-raw-schema schema/unit-statistics-raw-schema)
(def unit-statistics-transformer schema/unit-statistics-transformer)

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

;;; ─── Tournament DB queries ────────────────────────────────────────────────

(def get-tournament-by-eid query.tournament/get-tournament-by-eid)
(def get-tournaments-for-game query.tournament/get-tournaments-for-game)
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
(def get-matches-for-round query.tournament/get-matches-for-round)
(def update-match-result query.tournament/update-match-result)
(def create-game query.tournament/create-game)
(def get-games-for-match query.tournament/get-games-for-match)

;;; ─── League / Season DB entities ───────────────────────────────────────────

(def league-entity schema/league-entity)
(def create-league-params schema/create-league-params)
(def season-entity schema/season-entity)
(def create-season-params schema/create-season-params)
(def faction-standings-row-entity schema/faction-standings-row-entity)
(def max-ordinal-entity schema/max-ordinal-entity)

;;; ─── League DB queries ─────────────────────────────────────────────────────

(def get-league-by-eid query.league/get-league-by-eid)
(def get-leagues-for-game query.league/get-leagues-for-game)
(def create-league query.league/create-league)
(def update-league query.league/update-league)

;;; ─── Season DB queries ─────────────────────────────────────────────────────

(def get-season-by-eid query.season/get-season-by-eid)
(def get-seasons-for-league query.season/get-seasons-for-league)
(def get-current-season-for-league query.season/get-current-season-for-league)
(def get-max-ordinal-for-league query.season/get-max-ordinal-for-league)
(def create-season query.season/create-season)

;;; ─── Stats DB queries ──────────────────────────────────────────────────────

(def get-faction-standings-for-game query.stats/get-faction-standings-for-game)
(def get-faction-standings-for-league query.stats/get-faction-standings-for-league)
(def get-faction-standings-for-season query.stats/get-faction-standings-for-season)

;;; ─── Social Media DB entities ──────────────────────────────────────────────

(def platform-entity schema/platform-entity)

;;; ─── Social Media DB queries ──────────────────────────────────────────────

(def get-platform-by-eid query.social-media/get-platform-by-eid)
