(ns com.devereux-henley.rts-domain.contract
  (:require
   [com.devereux-henley.rts-domain.handlers.draft :as handlers.draft]
   [com.devereux-henley.rts-domain.handlers.game :as handlers.game]
   [com.devereux-henley.rts-domain.handlers.social-media :as handlers.social-media]
   [com.devereux-henley.rts-domain.schema :as schema]
   [malli.util]))

;;; ─── API resource schemas ──────────────────────────────────────────────────

(def social-media-platform-resource schema/social-media-platform-resource)

(def game-social-link-resource schema/game-social-link-resource)

(def draft-resource schema/draft-resource)

(def create-draft-specification       schema/create-draft-specification)
(def add-unit-to-draft-specification  schema/add-unit-to-draft-specification)

(def draft-error-response schema/draft-error-response)

(def draft-unit-resource  schema/draft-unit-resource)
(def draft-entry-resource schema/draft-entry-resource)

(def draft-add-response    schema/draft-add-response)
(def draft-remove-response schema/draft-remove-response)
(def draft-update-response schema/draft-update-response)

(def faction-resource schema/faction-resource)

(def game-resource schema/game-resource)

(def game-collection-resource schema/game-collection-resource)

(def resource-identifier schema/resource-identifier)

;;; ─── Handler-level functions (typed domain models) ─────────────────────────

;;; ─── Game handler functions ────────────────────────────────────────────────

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

;;; ─── Draft handler functions ────────────────────────────────────────────────

(def get-draft-by-eid                           handlers.draft/get-draft-by-eid)
(def get-drafts-for-player                      handlers.draft/get-drafts-for-player)
(def get-drafts-for-player-by-game              handlers.draft/get-drafts-for-player-by-game)
(def create-draft                               handlers.draft/create-draft)
(def get-draft-state                            handlers.draft/get-draft-state)
(def set-draft-state                            handlers.draft/set-draft-state)
(def parse-unit-statistics                      handlers.draft/parse-unit-statistics)
(def get-spells-by-keys                         handlers.draft/get-spells-by-keys)
(def get-abilities-by-keys                      handlers.draft/get-abilities-by-keys)
(def get-items-for-unit                         handlers.draft/get-items-for-unit)
(def get-mounts-for-unit                         handlers.draft/get-mounts-for-unit)
(def hydrate-units-with-stats                   handlers.draft/hydrate-units-with-stats)
(def build-section-context                      handlers.draft/build-section-context)
(def get-draft-unit-details                     handlers.draft/get-draft-unit-details)
(def get-draft-entry-details                    handlers.draft/get-draft-entry-details)
(def add-unit-to-draft                          handlers.draft/add-unit-to-draft)
(def update-unit-in-draft                       handlers.draft/update-unit-in-draft)
(def remove-unit-from-draft                     handlers.draft/remove-unit-from-draft)
(def get-draft-entry                            handlers.draft/get-draft-entry)

(def get-platform-by-eid                        handlers.social-media/get-platform-by-eid)
