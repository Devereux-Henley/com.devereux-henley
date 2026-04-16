# Flow: Tournament Registration

Covers player registration and withdrawal for tournaments. Registration is gated by the tournament's status, time window, and closed-early flag.

## Registration open — not yet registered

When a player views a tournament with an open registration window and is not yet registered, they see a "Register" button. The registration list shows any players who have already registered.

![Not registered](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/reg-not-registered.png)

## After registering

After clicking "Register" (POST `/api/tournament/:eid/entry/me`), the player appears in the registration list and the button changes to "Withdraw".

![Registered](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/reg-registered.png)

## Multiple players registered

As more players register, the list grows. Each entry shows the player's subject identifier and registration timestamp.

![Multiple players](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/reg-multiple-players.png)

## After withdrawal

A player who withdraws (DELETE `/api/tournament/:eid/entry/me`) is removed from the active registration list. They see the "Register" button again and can re-register if the window is still open.

![After withdrawal](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/reg-after-withdrawal.png)

## Validation rules

| Rule | Behaviour |
|------|-----------|
| Duplicate entry | 422 — "Already entered in this tournament." (DB UNIQUE constraint) |
| Registration closed (time window) | 422 — "Registration is not open." |
| Registration closed (manually) | 422 — "Registration is not open." (`closed-early` flag) |
| Withdrawal outside registration status | 422 — "Cannot withdraw outside of registration period." |

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/tournament/:eid/entry/me` | Create entry for the session user (201 or 422) |
| DELETE | `/api/tournament/:eid/entry/me` | Remove the session user's entry (200 or 422) |
| GET | `/api/tournament/:eid/entry` | List active entries |
