(ns com.devereux-henley.rts-data-access.contract
  (:require
   [com.devereux-henley.rts-data-access.query.game :as query.game]
   [com.devereux-henley.rts-data-access.query.social-media :as query.social-media]
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
(def get-abilities-by-names query.game/get-abilities-by-names)

(def draft-state-entity schema/draft-state-entity)
(def get-draft-state-by-draft query.game/get-draft-state-by-draft)
(def upsert-draft-state query.game/upsert-draft-state)

;;; ─── Social Media DB entities ──────────────────────────────────────────────

(def platform-entity schema/platform-entity)

;;; ─── Social Media DB queries ──────────────────────────────────────────────

(def get-platform-by-eid query.social-media/get-platform-by-eid)

;;; ─── Tournament DB entities ────────────────────────────────────────────────

(def create-tournament-params schema/create-tournament-params)

(def tournament-entity schema/tournament-entity)

(def tournament-snapshot-entity schema/tournament-snapshot-entity)

(def update-tournament-snapshot-specification schema/update-tournament-snapshot-specification)

;;; ─── Tournament DB queries ────────────────────────────────────────────────

(def get-tournaments query.tournament/get-tournaments)

(def get-tournaments-by-game-eid query.tournament/get-tournaments-by-game-eid)

(def get-tournament-by-eid query.tournament/get-tournament-by-eid)

(def get-tournament-snapshot-by-eid query.tournament/get-tournament-snapshot-by-eid)

(def get-tournament-snapshot-by-tournament-eid query.tournament/get-tournament-snapshot-by-tournament-eid)

(def create-tournament query.tournament/create-tournament)

(def update-tournament-snapshot-by-tournament-eid query.tournament/update-tournament-snapshot-by-tournament-eid)
