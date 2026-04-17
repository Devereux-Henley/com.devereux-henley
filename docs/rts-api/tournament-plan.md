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

## MVP 5+6: Phase Management, Swiss Pairing, Game Sub-resource, Elimination

Automated phase progression with Swiss pairing, best-of-N match formats via the game sub-resource, and seeded single-elimination brackets. Phase progression is generic — any sequence of phase types is valid.

### New tables

- `000025-create-game-table.up.sql` — `match_game` table for individual game results within matches
- `000026-add-match-format.up.sql` — adds `format` column to `match` table (default 1 for bo1)

### Phase configuration

Phases are configured during registration via `PUT /api/tournament/:eid/phase`. Each phase has a `phase-type` and a list of rounds with optional format (bo1/bo3/bo5). Any sequence of phase types is valid.

```clojure
{:phases [{:phase-type "swiss"
           :rounds [{:round-index 0 :format 1}
                    {:round-index 1 :format 1}
                    {:round-index 2 :format 3}]}
          {:phase-type "single-elimination"
           :rounds [{:round-index 0 :format 3}
                    {:round-index 1 :format 5}]}]}
```

Enums: `phase-type` is `swiss | round-robin | single-elimination | double-elimination`. `format` is `1 | 3 | 5`.

### Game sub-resource

Games are individual results within a match. A match with `format=1` (bo1) completes after one game. The match auto-completes when a player reaches the win threshold (`ceil(format / 2)`). Standings recalculate only on match completion.

Bye matches (nil `player-two-sub`) are auto-completed on creation.

### Round generation and phase advancement

`POST /api/tournament/:eid/round` generates the next round for the current phase. Pairing strategy dispatches on `phase-type`:

| Phase type | Pairing strategy |
|---|---|
| `swiss` | Standings-based, avoids repeat matchups, bye for odd count |
| `round-robin` | Same as Swiss (pairing logic) |
| `single-elimination` | Seeded bracket (1vN, 2v(N-1)), byes for non-power-of-2 |
| `double-elimination` | Same bracket generation (losers bracket not yet implemented) |

When the current phase has no more rounds configured, `generate-next-round` auto-advances to the next phase and generates its first round. The response includes `{:phase-advanced true}`.

### Tournament auto-completion

When the last match in the last round of the last phase completes, the tournament status auto-transitions to `"complete"`. Without phase configuration (manual match management), auto-completion does not occur.

### Rules (`rules/tournament.clj`)

- `swiss-pair [standings match-history]` — standings-based pairing avoiding repeats, bye handling
- `match-win-threshold [format]` — `(inc (quot format 2))`
- `check-match-complete [games format]` — returns winner-sub if threshold reached
- `generate-elimination-bracket [qualified-players]` — seeded 1vN bracket with byes
- `recalculate-standings` — win=3pts, draw=1pt, byes ignored

### Handlers (`handlers/tournament.clj`)

- `configure-phases` — sets phase config during registration (organizer only)
- `generate-next-round` — generates next round or auto-advances phase (organizer only)
- `record-game-result` — records game, auto-completes match at threshold, recalculates standings and checks tournament completion
- `recalculate-and-check-completion` — shared helper for standings + auto-completion

### Routes

| Method | Path | Description |
|---|---|---|
| `PUT` | `/api/tournament/:eid/phase` | Update phase configuration |
| `POST` | `/api/tournament/:eid/round` | Create next round of matches |
| `POST` | `/api/tournament/:eid/match/:match-eid/game` | Record a game result |
| `GET` | `/api/tournament/:eid/match/:match-eid/game` | List games for a match |

### Verification

- Configure Swiss (bo1) + elimination (bo3) phases
- Advance to active, generate rounds, record results
- Verify no repeat Swiss pairings, bye handling for odd player count
- Verify bo3 match requires 2 wins to complete
- Verify standings only update on match completion
- Verify auto-advance from Swiss to elimination
- Verify tournament auto-completes after final match

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
