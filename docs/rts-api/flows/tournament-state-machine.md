# Flow: Tournament State Machine

Covers the tournament lifecycle: registration, active, complete, and cancelled states. Only the tournament organizer (`created-by-sub`) can advance or cancel.

## State transitions

```
registration → active → complete
             ↘ cancelled ←↙
```

Terminal states (`complete`, `cancelled`) accept no further transitions.

## Registration status (organizer view)

The organizer sees three action buttons: "Close Registration" (sets `closed-early` flag, blocking new entries), "Start Tournament" (transitions to active, populates standings), and "Cancel Tournament".

![Registration organizer view](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/sm-registration-organizer.png)

## Active status with standings

After advancing to active, the standings table appears with all entered players initialized to 0 wins/losses/draws/points. The organizer sees "Complete Tournament" and "Cancel Tournament" buttons. Entry/withdraw buttons are no longer shown.

![Active with standings](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/sm-active-standings.png)

## Completed tournament

After completing, the standings table remains visible but no action buttons are shown — complete is a terminal state.

![Completed](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/sm-complete.png)

## Validation rules

| Rule | Behaviour |
|------|-----------|
| Invalid transition (e.g. registration → complete) | 422 — transition error |
| Non-organizer attempts advance | 422 — "Only the tournament organizer can advance" |
| Close registration when not in registration status | 422 — error |
| Close registration early | Sets `closed-early` flag; new entries get 422 |

## API endpoints

| Method | Path | Body | Description |
|--------|------|------|-------------|
| POST | `/api/tournament/:eid/advance` | `{"target-status": "active"\|"complete"\|"cancelled"}` | Advance tournament state |
| POST | `/api/tournament/:eid/close-registration` | — | Close registration early |
