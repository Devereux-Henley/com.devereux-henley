# Flow: Tournament Matches

Covers match creation, result recording, and standings updates. Matches are nested subresources of a tournament and can only be created when the tournament is active.

## Active tournament with pending match

After activating a tournament and creating a match, the matches table appears with the matchup, round, and "pending" status. Standings show both players at 0/0/0.

![Pending match](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/match-pending.png)

## After recording a result

Recording a result via `PUT /api/tournament/:eid/match/:match-eid/result` sets the match status to "complete", records the winner, and recalculates standings. Win = 3 points, draw = 1 point each, loss = 0 points. Byes are ignored.

![Result recorded](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/match-result-recorded.png)

## Validation rules

| Rule | Behaviour |
|------|-----------|
| Create match when tournament not active | 422 — "Tournament must be active to create matches." |
| Record result on non-pending match | 422 — "Match is not pending." |
| Match not found | 422 — "Match not found." |

## API endpoints

| Method | Path | Body | Description |
|--------|------|------|-------------|
| GET | `/api/tournament/:eid/match` | — | List all matches |
| POST | `/api/tournament/:eid/match` | `{phase-index, round-index, player-one-sub, player-two-sub}` | Create match (201 or 422) |
| GET | `/api/tournament/:eid/match/:match-eid` | — | Get match by eid |
| PUT | `/api/tournament/:eid/match/:match-eid/result` | `{winner-sub}` | Record result, recalculate standings |
