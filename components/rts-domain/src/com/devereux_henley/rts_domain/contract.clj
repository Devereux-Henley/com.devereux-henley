(ns com.devereux-henley.rts-domain.contract
  (:require
   [com.devereux-henley.rts-domain.handlers.game :as handlers.game]
   [com.devereux-henley.rts-domain.handlers.social-media :as handlers.social-media]
   [com.devereux-henley.rts-domain.handlers.tournament :as handlers.tournament]
   [com.devereux-henley.rts-domain.schema :as schema]
   [malli.util]))

;;; ─── API resource schemas ──────────────────────────────────────────────────

(def social-media-platform-resource schema/social-media-platform-resource)

(def game-social-link-resource schema/game-social-link-resource)

(def draft-resource schema/draft-resource)

(def create-draft-specification schema/create-draft-specification)

(def faction-resource schema/faction-resource)

(def game-resource schema/game-resource)

(def tournament-snapshot-resource schema/tournament-snapshot-resource)

(def tournament-resource schema/tournament-resource)

(def game-collection-resource schema/game-collection-resource)

(def tournament-collection-resource schema/tournament-collection-resource)

(def resource-identifier schema/resource-identifier)

(def create-tournament-specification schema/create-tournament-specification)

;;; ─── Handler-level functions (typed domain models) ─────────────────────────

(def get-draft-by-eid                           handlers.game/get-draft-by-eid)
(def get-drafts-for-player                      handlers.game/get-drafts-for-player)
(def get-drafts-for-player-by-game              handlers.game/get-drafts-for-player-by-game)
(def create-draft                               handlers.game/create-draft)
(def get-game-by-eid                            handlers.game/get-game-by-eid)
(def get-games                                  handlers.game/get-games)
(def get-factions-for-game                      handlers.game/get-factions-for-game)
(def get-faction-by-eid                         handlers.game/get-faction-by-eid)
(def get-socials-for-game                       handlers.game/get-socials-for-game)
(def get-game-social-link-by-eid                handlers.game/get-game-social-link-by-eid)
(def get-units-for-game                         handlers.game/get-units-for-game)
(def get-unit-by-eid                            handlers.game/get-unit-by-eid)
(def get-units-for-faction                      handlers.game/get-units-for-faction)
(def get-game-mode-by-eid                       handlers.game/get-game-mode-by-eid)
(def get-game-modes-for-game                    handlers.game/get-game-modes-for-game)
(def get-spells-by-keys                         handlers.game/get-spells-by-keys)
(def get-abilities-by-names                     handlers.game/get-abilities-by-names)
(def parse-unit-statistics                      handlers.game/parse-unit-statistics)
(def get-draft-state                            handlers.game/get-draft-state)
(def set-draft-state                            handlers.game/set-draft-state)

(def get-platform-by-eid                        handlers.social-media/get-platform-by-eid)

(def get-tournaments                            handlers.tournament/get-tournaments)
(def get-tournaments-by-game-eid                handlers.tournament/get-tournaments-by-game-eid)
(def get-tournament-by-eid                      handlers.tournament/get-tournament-by-eid)
(def get-tournament-snapshot-by-eid             handlers.tournament/get-tournament-snapshot-by-eid)
(def get-tournament-snapshot-by-tournament-eid  handlers.tournament/get-tournament-snapshot-by-tournament-eid)
(def create-tournament                          handlers.tournament/create-tournament)
(def update-tournament-snapshot                 handlers.tournament/update-tournament-snapshot)
