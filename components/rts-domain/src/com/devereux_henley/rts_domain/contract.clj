(ns com.devereux-henley.rts-domain.contract
  (:require
   [com.devereux-henley.rts-domain.handlers.draft :as handlers.draft]
   [com.devereux-henley.rts-domain.handlers.game :as handlers.game]
   [com.devereux-henley.rts-domain.handlers.social-media :as handlers.social-media]
   [com.devereux-henley.rts-domain.handlers.tournament :as handlers.tournament]
   [com.devereux-henley.rts-domain.rules.tournament :as rules.tournament]
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
(def embed-unit-for-entry                       handlers.draft/embed-unit-for-entry)
(def add-unit-to-draft                          handlers.draft/add-unit-to-draft)
(def update-unit-in-draft                       handlers.draft/update-unit-in-draft)
(def remove-unit-from-draft                     handlers.draft/remove-unit-from-draft)
(def get-draft-entry                            handlers.draft/get-draft-entry)

;;; ─── Tournament handler functions ──────────────────────────────────────────

(def tournament-resource                          schema/tournament-resource)
(def create-tournament-specification              schema/create-tournament-specification)
(def tournament-collection-resource               schema/tournament-collection-resource)
(def tournament-entry-resource                     schema/tournament-entry-resource)

(def get-tournament-by-eid                        handlers.tournament/get-tournament-by-eid)
(def get-tournaments-for-game                     handlers.tournament/get-tournaments-for-game)
(def create-tournament                            handlers.tournament/create-tournament)
(def get-tournament-state                         handlers.tournament/get-tournament-state)
(def set-tournament-state                         handlers.tournament/set-tournament-state)
(def create-entry                                 handlers.tournament/create-entry)
(def delete-entry                                 handlers.tournament/delete-entry)
(def get-entries                                   handlers.tournament/get-entries)
(def is-registration-open?                        handlers.tournament/is-registration-open?)
(def match-resource                               schema/match-resource)
(def create-match-specification                   schema/create-match-specification)
(def record-result-specification                  schema/record-result-specification)
(def update-status-specification                  schema/update-status-specification)
(def update-registration-specification            schema/update-registration-specification)
(def configure-phases-specification               schema/configure-phases-specification)
(def phase-response                                schema/phase-response)
(def round-response                                schema/round-response)
(def bracket-match-slot                            schema/bracket-match-slot)
(def bracket-round                                 schema/bracket-round)
(def phase-group                                   schema/phase-group)
(def tournament-entries-response                  schema/tournament-entries-response)
(def tournament-status-response                   schema/tournament-status-response)
(def tournament-advance-response                  schema/tournament-advance-response)
(def tournament-registration-response             schema/tournament-registration-response)
(def tournament-entry-deleted-response            schema/tournament-entry-deleted-response)
(def tournament-matches-response                  schema/tournament-matches-response)
(def tournament-match-result-response             schema/tournament-match-result-response)

(def available-transitions                        handlers.tournament/available-transitions)
(def advance-tournament                           handlers.tournament/advance-tournament)
(def close-registration-early                     handlers.tournament/close-registration-early)
(def create-match                                 handlers.tournament/create-match)
(def get-match-by-eid                             handlers.tournament/get-match-by-eid)
(def get-matches-for-tournament                   handlers.tournament/get-matches-for-tournament)
(def get-matches-for-round                        handlers.tournament/get-matches-for-round)
(def update-match-result                          handlers.tournament/update-match-result)
(def record-game-result                           handlers.tournament/record-game-result)
(def get-games-for-match                          handlers.tournament/get-games-for-match)
(def configure-phases                             handlers.tournament/configure-phases)
(def generate-next-round                          handlers.tournament/generate-next-round)

(def common-timezones                             rules.tournament/common-timezones)
(def default-timezone                             rules.tournament/default-timezone)
(def group-matches-by-round                       rules.tournament/group-matches-by-round)
(def group-matches-by-phase                       rules.tournament/group-matches-by-phase)

;;; ─── Social Media handler functions ────────────────────────────────────────

(def get-platform-by-eid                        handlers.social-media/get-platform-by-eid)
