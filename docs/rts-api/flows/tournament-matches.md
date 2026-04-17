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
