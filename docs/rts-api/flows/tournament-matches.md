# Flow: Tournament Matches

Covers match creation, result recording, and standings updates. Matches are nested subresources of a tournament and can only be created when the tournament is active.

## Active tournament with pending match

After activating a tournament and creating a match, the matches table appears with the matchup, round, and "pending" status. Standings show both players at 0/0/0.

![Pending match](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/match-pending.png)

## After recording a result

Recording a result via `PUT /api/tournament/:eid/match/:match-eid/result` sets the match status to "complete", records the winner, and recalculates standings. Win = 3 points, draw = 1 point each, loss = 0 points. Byes are ignored.

![Result recorded](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/match-result-recorded.png)

## Best-of-N matches (game sub-resource)

Matches have a `format` field (1, 3, or 5) that determines how many games are needed to win. For bo1, a single game completes the match. For bo3, the first player to win 2 games wins the match. Games are recorded via `POST /api/tournament/:eid/match/:match-eid/game`. The match auto-completes when the win threshold is reached, and standings recalculate only on match completion.

## Recording a match from replays

The post-match modal at `/view/match-record/:match-eid/index.html` accepts one `.replay` file per game. On submit, the server parses each replay via the Rust `tw-replay-parser` binary, derives a winner from the form's `winner-sub-N` fields, and persists three things per game:

1. A `replay` row with the engine-resolved JSON blob and the uploader's local alliance index.
2. A `match_game` row linking the replay to the match by `game_index`.
3. Two `draft` rows + `draft_state` blobs — one per match side — capturing the units that were played, with mount keys resolved from each parser key's mount suffix and spell keys pulled directly from the parser's `:spells` array. The `match_game` row's `player_one_draft_id` / `player_two_draft_id` columns reference these auto-created drafts, which immediately renders them read-only (see [draft-operations § Locked drafts](./draft-operations.md#locked-read-only-drafts)).

Each auto-created entry persists `:total-cost` (recomputed via the same domain function the unit panel uses) alongside `:engine-cost` (the parser's engine-resolved cost) — a divergence between the two surfaces as a ⚠ indicator on the slot card.

## Per-game lineup links on the phase view

Once a match has recorded games, its card on the phase view exposes a per-game "G&lt;N&gt; lineup" link to each side's auto-created draft. The links resolve to the read-only draft URL at `/view/game/:game-eid/draft/:eid/index.html`, so spectators can drill into the army lists that were played without leaving the bracket context.

![Match phase with lineup links](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/readonly-drafts/tournament-lineup-links.png)

## Validation rules

| Rule | Behaviour |
|------|-----------|
| Create match when tournament not active | 422 — "Tournament must be active to create matches." |
| Record result on non-pending match | 422 — "Match is not pending." |
| Record game on completed match | 422 — "Match is already complete." |
| Match not found | 422 — "Match not found." |

## API endpoints

| Method | Path | Body | Description |
|--------|------|------|-------------|
| GET | `/api/tournament/:eid/match` | — | List all matches |
| POST | `/api/tournament/:eid/match` | `{phase-index, round-index, player-one-sub, player-two-sub}` | Create match (201 or 422) |
| GET | `/api/tournament/:eid/match/:match-eid` | — | Get match by eid |
| PUT | `/api/tournament/:eid/match/:match-eid/result` | `{winner-sub}` | Record result directly (bo1 shortcut) |
| POST | `/api/tournament/:eid/match/:match-eid/game` | `{winner-sub}` | Record a game (auto-completes match at threshold) |
| GET | `/api/tournament/:eid/match/:match-eid/game` | — | List games for a match |
