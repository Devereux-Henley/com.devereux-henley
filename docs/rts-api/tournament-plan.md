# Tournament Feature Implementation Plan

## Context

The platform needs tournaments to organize competitive play. A tournament groups matches, supports phased formats (e.g. Swiss rounds followed by single elimination), and manages player registration with a configurable open/close window. Tournaments are scoped to a game via FK.

## Design Decisions

- **Tournament state** lives in a separate `tournament_state` table as a JSON blob (TEXT column), following the `draft_state` pattern. The tournament row stays narrow for listing queries.
- **Registrations** are rows in their own table (not JSON blob entries) for referential integrity and `UNIQUE(tournament_id, player_sub)` deduplication.
- **Matches** are rows in their own table with `phase_index` and `round_index` columns for efficient querying without parsing the state blob.
- **Registration window** uses both timestamps (opens-at/closes-at) and manual organizer control (close early / extend).
- **Phase config** is stored in the state blob's `:phases` array. Each phase has a `:phase-type` (`"swiss"` or `"single-elimination"`), its own status, config, and rounds.

## Tournament State Blob Shape

Designed upfront; populated incrementally across MVPs.

```clojure
{:status          "registration" ;; "registration" | "active" | "complete" | "cancelled"
 :registration    {:opens-at     "2026-05-01T00:00:00Z"
                   :closes-at    "2026-05-15T00:00:00Z"
                   :closed-early false}
 :phases          [{:phase-type  "swiss"
                    :phase-index 0
                    :status      "pending" ;; "pending" | "active" | "complete"
                    :config      {:rounds 3}
                    :rounds      [{:round-index 0
                                   :status      "pending"
                                   :match-eids  []}]}]
 :current-phase   nil
 :standings       [] ;; [{:player-sub "..." :wins 0 :losses 0 :draws 0 :points 0}]
 :qualifier-count 8} ;; how many advance from swiss to elimination
```

---

## MVP 1: Core Tournament Entity (CRUD)

Create, fetch, and list tournaments. State blob initialized but not yet acted upon.

### Migrations

- `000021-create-tournament-table.up.sql`

```sql
CREATE TABLE IF NOT EXISTS tournament (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  game_id INTEGER NOT NULL,
  created_by_sub TEXT NOT NULL,
  version INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  FOREIGN KEY(game_id) REFERENCES game(id)
);
```

- `000022-create-tournament-state-table.up.sql`

```sql
CREATE TABLE IF NOT EXISTS tournament_state (
  id INTEGER PRIMARY KEY ASC,
  tournament_id INTEGER NOT NULL,
  state TEXT NOT NULL DEFAULT '{}',
  updated_at TEXT NOT NULL,
  UNIQUE(tournament_id),
  FOREIGN KEY(tournament_id) REFERENCES tournament(id)
);
```

### Data-access layer

- **Entity schemas** in `schema.clj`: `tournament-entity`, `create-tournament-params`, `tournament-state-entity`
- **New file:** `query/tournament.clj` with query fns + SQL files in `resources/.../sql/tournament/`:
  - `get-tournament-by-eid.sql` (joins game for `game_eid`)
  - `get-tournaments-for-game.sql`
  - `create-tournament.sql`
  - `get-tournament-state.sql`
  - `upsert-tournament-state.sql`
- **Contract exports** in `contract.clj`

### Domain layer

- **New file:** `handlers/tournament.clj`
  - `get-tournament-by-eid`, `get-tournaments-for-game`, `create-tournament`
  - `get-tournament-state`, `set-tournament-state` (JSON serialize/deserialize via jsonista)
- **Resource schemas** in `schema.clj`:
  - `tournament-resource` (merged with `base-resource`, `[:eid {:model/link :tournament/by-eid}]`, `[:game-eid {:model/link :game/by-eid}]`, `[:type [:= :tournament/tournament]]`)
  - `create-tournament-specification`
  - `tournament-collection-resource`
- **Contract exports**

### Web layer

- **New file:** `web/tournament.clj` with Integrant init-key handlers:
  - `::get-tournament`, `::get-tournaments`, `::create-tournament`
- **Routes** in `routes.clj`:
  - `GET/PUT /api/tournament/:eid` (`:name :tournament/by-eid`)
  - `GET /api/game/:game-eid/tournaments`
- **Templates:** tournament list, create form, tournament detail page
- **Configuration:** wire handlers in `configuration.clj`

### Initial state blob on create

```clojure
{:status "registration"
 :registration {:opens-at <from-spec> :closes-at <from-spec> :closed-early false}
 :phases []
 :current-phase nil
 :standings []
 :qualifier-count nil}
```

### Verification

- Start dev server with `(go!)`
- Create a tournament via PUT, fetch by eid, list for a game
- Verify HAL+JSON `_links` resolve correctly
- Verify HTML templates render

---

## MVP 2: Tournament Registration

Players register/withdraw during the registration window.

### Migration

- `000023-create-tournament-registration-table.up.sql`

```sql
CREATE TABLE IF NOT EXISTS tournament_registration (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  tournament_id INTEGER NOT NULL,
  player_sub TEXT NOT NULL,
  registered_at TEXT NOT NULL,
  withdrawn_at TEXT,
  UNIQUE(eid),
  UNIQUE(tournament_id, player_sub),
  FOREIGN KEY(tournament_id) REFERENCES tournament(id)
);
```

### Data-access layer

- **Entity schema:** `tournament-registration-entity`
- **SQL files:** `register-player.sql`, `withdraw-player.sql`, `get-registrations-for-tournament.sql`, `get-registration-by-tournament-and-player.sql`
- **Query fns** added to `query/tournament.clj`

### Domain layer

- **Handlers:**
  - `register-player [deps tournament-eid player-sub]` -- validates registration window open (timestamps + `closed-early` flag + tournament status)
  - `withdraw-player [deps tournament-eid player-sub]` -- validates tournament still in `"registration"` status
  - `get-registrations [deps tournament-eid]`
  - `is-registration-open? [state now]` -- pure fn checking window and status
- **Resource schema:** `tournament-registration-resource` with `:type [:= :tournament/registration]`

### Web layer

- **Routes:**
  - `POST /api/tournament/:eid/register` (player-sub from session)
  - `DELETE /api/tournament/:eid/register` (withdraw)
  - `GET /api/tournament/:eid/registrations`
- **Templates:** registration list partial, register/withdraw button on tournament detail

### Verification

- Register as dev-player-one and dev-player-two
- Verify UNIQUE constraint prevents double registration
- Verify time window enforcement
- Verify withdrawal sets `withdrawn_at`

---

## MVP 3: Tournament State Machine

Organizer advances tournament through lifecycle: registration -> active -> complete/cancelled.

### No new tables

Pure domain logic on the state blob.

### Domain layer

- **New file:** `rules/tournament.clj`
  - `valid-transitions` map: `{"registration" #{"active" "cancelled"}, "active" #{"complete" "cancelled"}}`
  - `validate-transition [current target]` -- returns nil or error map
  - `close-registration [state registrations]` -- populates `:standings` from active registrations, sets status to `"active"`
- **Handlers:**
  - `advance-tournament [deps tournament-eid target-status]` -- validates transition, applies side effects, persists state
  - `close-registration-early [deps tournament-eid]` -- sets `:closed-early true` in state blob
  - Authorization: only `created_by_sub` can advance

### Web layer

- **Routes:**
  - `POST /api/tournament/:eid/advance` with body `{:target-status "active"}`
  - `POST /api/tournament/:eid/close-registration`
- **Templates:** state indicator, advance/close buttons on tournament detail, standings table

### Verification

- Advance from registration -> active, verify standings populated from registrations
- Verify invalid transitions rejected
- Verify only organizer can advance
- Verify close-registration-early prevents new registrations

---

## MVP 4: Match Entity and Assignment

Matches can be created within a tournament and have results recorded.

### Migration

- `000024-create-match-table.up.sql`

```sql
CREATE TABLE IF NOT EXISTS match (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  tournament_id INTEGER NOT NULL,
  phase_index INTEGER NOT NULL,
  round_index INTEGER NOT NULL,
  player_one_sub TEXT NOT NULL,
  player_two_sub TEXT,
  winner_sub TEXT,
  status TEXT NOT NULL DEFAULT 'pending',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(eid),
  FOREIGN KEY(tournament_id) REFERENCES tournament(id)
);
```

`player_two_sub` nullable for byes. `winner_sub` nullable until resolved.

### Data-access layer

- **Entity schema:** `match-entity`
- **SQL files:** `create-match.sql`, `get-match-by-eid.sql`, `get-matches-for-tournament.sql`, `get-matches-for-round.sql`, `update-match-result.sql`

### Domain layer

- **Handlers:**
  - `create-match [deps tournament-eid match-spec]` -- validates tournament is active
  - `update-match-result [deps match-eid winner-sub]` -- records result, recalculates standings in state blob
  - `get-matches-for-tournament`, `get-matches-for-round`
- **Resource schema:** `match-resource` with `:type [:= :tournament/match]`
- **Result flow:** recording a result updates the match row, then recalculates standings and checks round completion in the state blob

### Web layer

- **Routes:**
  - `GET /api/tournament/:tournament-eid/matches`
  - `GET/PUT /api/tournament/:tournament-eid/match/:eid` (`:name :match/by-eid`)
  - `POST /api/match/:eid/result`
- **Templates:** match list (grouped by round), match detail with result form

### Verification

- Create matches manually for a tournament
- Record results, verify standings update
- Verify match status transitions

---

## MVP 5: Swiss Round Phase Logic

Automated Swiss pairing based on standings after each round. Also introduces the game sub-resource for best-of-N match formats.

### New table

- `000025-create-game-table.up.sql`

```sql
CREATE TABLE IF NOT EXISTS game (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  match_id INTEGER NOT NULL,
  game_index INTEGER NOT NULL,
  winner_sub TEXT,
  created_at TEXT NOT NULL,
  UNIQUE(eid),
  FOREIGN KEY(match_id) REFERENCES match(id)
);
```

Add `format` column to `match` table (default 1 for bo1):

- `000026-add-match-format.up.sql`: `ALTER TABLE match ADD COLUMN format INTEGER NOT NULL DEFAULT 1;`

### Round format configuration

Each round in the phase config specifies a `format` (best-of-N). Matches created for that round inherit the format. The match auto-completes when one player reaches the win threshold (`ceil(format / 2)`).

```clojure
{:phases [{:phase-type "swiss"
           :rounds [{:round-index 0 :format 1}    ;; bo1
                    {:round-index 1 :format 1}
                    {:round-index 2 :format 3}]}  ;; bo3 for final swiss round
          {:phase-type "single-elimination"
           :rounds [{:round-index 0 :format 3}    ;; bo3 quarterfinals
                    {:round-index 1 :format 3}    ;; bo3 semis
                    {:round-index 2 :format 5}]}]} ;; bo5 grand final
```

### Game sub-resource

Games are individual results within a match. A match with `format=1` (bo1) behaves identically to the current MVP 4 flow (one game = match complete).

- `POST /api/tournament/:eid/match/:match-eid/game` with `{winner-sub}` — records a game result
- `GET /api/tournament/:eid/match/:match-eid/game` — list games for a match
- Match auto-completes when a player reaches the win threshold; standings recalculate on match completion

### Domain layer

- **In `rules/tournament.clj`:**
  - `swiss-pair [standings match-history]` -- pure fn: sort by points, pair adjacent unplayed opponents, handle byes
  - `generate-swiss-round [state match-history]` -- returns updated state with new round
  - `match-win-threshold [format]` -- `(inc (quot format 2))`
  - `check-match-complete [games format]` -- returns winner-sub if threshold reached
- **Handlers:**
  - `configure-phases [deps tournament-eid phase-config]` -- sets `:phases` and `:qualifier-count` in state blob (called before advancing to active)
  - `generate-next-round [deps tournament-eid]` -- validates current round complete, computes pairings, creates match rows with round format, updates state blob
  - `complete-phase [deps tournament-eid]` -- marks current phase complete, advances `:current-phase`
  - `record-game-result [deps match-eid winner-sub]` -- records game, checks if match completes, recalculates standings on completion

### Web layer

- **Routes:**
  - `POST /api/tournament/:eid/configure-phases`
  - `POST /api/tournament/:eid/generate-round`
  - `POST /api/tournament/:eid/match/:match-eid/game`
  - `GET /api/tournament/:eid/match/:match-eid/game`
- **Templates:** standings table with points, "Generate Next Round" button, game list within match detail

### Verification

- Configure a Swiss phase with 3 rounds (bo1, bo1, bo3)
- Advance to active, generate round 1 pairings
- Record all results, generate round 2
- Verify no repeat pairings, verify bye handling for odd player count
- Verify bo3 match requires 2 wins to complete
- Verify standings only update on match completion, not individual games

---

## MVP 6: Single Elimination Phase Logic

Qualified players from Swiss enter a seeded elimination bracket.

### No new tables

### Domain layer

- **In `rules/tournament.clj`:**
  - `generate-elimination-bracket [qualified-players]` -- seed 1 vs N, 2 vs N-1; byes for non-power-of-2
  - `advance-elimination [state match-result]` -- slots winner into next round's matchup
- **Handlers:**
  - `start-elimination-phase [deps tournament-eid]` -- validates Swiss complete, generates bracket, creates first-round matches
  - Enhanced `update-match-result` -- after elimination match, advances winner to next round
  - `complete-tournament [deps tournament-eid]` -- transitions to `"complete"` after final match

### Web layer

- **Routes:**
  - `POST /api/tournament/:eid/start-elimination`
- **Templates:** bracket view (round-by-round list), final result display

### Verification

- Complete a Swiss phase, start elimination
- Verify seeding order matches standings
- Record results through bracket, verify advancement
- Verify tournament completes after final match

---

## Key Reference Files

| Layer | File |
|-------|------|
| Migration pattern | `components/rts-data/resources/rts-data/migrations/000009-create-draft-table.up.sql` |
| State blob pattern | `components/rts-data/resources/rts-data/migrations/000016-create-draft-state-table.up.sql` |
| Entity schemas | `components/rts-data-access/src/com/devereux_henley/rts_data_access/schema.clj` |
| Query pattern | `components/rts-data-access/src/com/devereux_henley/rts_data_access/query/game.clj` |
| DA contract | `components/rts-data-access/src/com/devereux_henley/rts_data_access/contract.clj` |
| Domain handlers | `components/rts-domain/src/com/devereux_henley/rts_domain/handlers/draft.clj` |
| Resource schemas | `components/rts-domain/src/com/devereux_henley/rts_domain/schema.clj` |
| Domain contract | `components/rts-domain/src/com/devereux_henley/rts_domain/contract.clj` |
| Web handlers | `components/rts-web/src/com/devereux_henley/rts_web/web/draft.clj` |
| Routes | `components/rts-web/src/com/devereux_henley/rts_web/web/routes.clj` |
| Configuration | `bases/rts-api/src/com/devereux_henley/rts_api/configuration.clj` |
