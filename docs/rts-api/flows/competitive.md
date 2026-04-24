# Flow: Competitive (Leagues + Seasons + Faction Stats)

Covers the per-game **Competitive** landing, league/season organization above tournaments, and the matched-play faction statistics that roll up at game / league / season scopes. Tested by `components/e2e/tests/league.spec.js`.

## Conceptual model

- A **league** groups one or more tournaments under a long-running competitive umbrella (per-game, owned by its creator).
- A **season** is an ordinally numbered window within a league (`Season 1`, `Season 2`, …) with fixed start/end dates. The ordinal is auto-assigned `MAX(ordinal) + 1` per league.
- A **tournament** can stand alone, belong to a league only (no specific season), or belong to a league + season. Attachment is set at creation time only — when `:season-eid` is provided, `:league-eid` is derived server-side from the season.
- **Matched-play faction stats** aggregate completed matches by the faction each player drafted for that match. Stats are computed on demand from the `match` → `draft` → `faction` join — no cache.

## Competitive landing

Every game has a single Competitive page at `/view/game/:game-eid/competitive/index.html`. It hosts two side-by-side tabs (WCAG-AA `role="tablist"`, Hyperscript-driven panel toggling — no HTMX swap needed):

- **Tournaments** — every tournament for the game, league-affiliated or standalone. League-affiliated cards carry a "League · Season N" badge that links to the league.
- **Leagues** — every league for the game. Each card shows the league name, description, current-season subline, and tournament count.

The legacy `/view/game/:game-eid/tournament/index.html` URL is removed (404). All tournament-related navigation now flows through the Competitive landing.

![Competitive landing — Tournaments tab](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-competitive-tournaments.png)

![Competitive landing — Leagues tab](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-competitive-leagues.png)

## Create league

`/view/game/:game-eid/league/create.html` is a small two-field form: name + description. The handler PUTs to `/api/league/:eid`, returns 201 with an `HX-Redirect` to the new league detail page.

![Create league form](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-create-league.png)

## League detail

`/view/game/:game-eid/league/:eid/index.html` shows:

1. **Header** — name, description, organizer; only the league owner sees the **Create Season** CTA.
2. **Seasons list** — every season in ordinal order, linking to season detail pages.
3. **Faction Standings** — `GET /api/stats/league/:eid/faction` results rendered as an accessible `<table>` (`<th scope="col">`, `<caption class="sr-only">`). Aggregates wins, losses, and win % across every completed match in every season-attached and season-less tournament under the league. Draws are not tracked — they are vanishingly rare in Warhammer matches and are typically replayed or omitted rather than recorded.
4. **Tournaments in this League** — flat list grouped visually by season (or `(no season)` for league-but-no-season tournaments).

![League detail with faction standings](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-league-detail.png)

## Create season

`/view/game/:game-eid/league/:league-eid/season/create.html` has an optional `name` override (defaults to `Season N`), a `timezone` select, and `start-at` / `end-at` `datetime-local` fields. `start-at` must precede `end-at` — the domain layer rejects with `{:type :season/error}` otherwise. Only the league owner can create seasons.

![Create season form](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-create-season.png)

## Season detail

`/view/game/:game-eid/league/:league-eid/season/:eid/index.html` shows the season header (display-name, parent league link, date range), faction standings scoped to the season, and the list of tournaments in the season.

![Season detail](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-season-detail.png)

## Tournament integration

### Create-tournament form gains a league picker

The create-tournament form has a new optional **League** fieldset above the registration window. The default is `— Standalone (no league) —`. Selecting a league fires an `hx-get` to `/view/game/:game-eid/competitive/season-options.html?league-eid=...`, which renders a season `<select>` into the wrapper `<div>` — letting the user pick a season under that league.

![Create tournament with league dropdown](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-create-tournament-league-dropdown.png)

### Tournament detail shows league badge

Tournaments attached to a league render a small badge in the hero (`Spring Showdown · Season 1`) that links to the league detail page. Standalone tournaments render no badge.

> The per-match **draft picker** that previously lived in the tournament sidebar — the assignment that powers faction-stats roll-ups — has been removed for now and will be re-introduced in a follow-up branch. Until then, the standings tables exist but stay empty because no `match.player_*_draft_id` is ever recorded through the UI.

## Stats endpoints

Three faction-stats endpoints, identical response shape — only the WHERE filter differs (game / league / season FK):

- `GET /api/stats/game/:game-eid/faction`
- `GET /api/stats/league/:league-eid/faction`
- `GET /api/stats/season/:season-eid/faction`

Each returns `{:type :stats/<scope>-faction-standings, :scope <"game"|"league"|"season">, :scope-eid …, :rows [{:type :stats/faction-row, :faction-eid …, :faction-name …, :matches-played, :wins, :losses, :win-percentage} …]}`. `:win-percentage` is computed in the domain handler (rounded integer, 0–100) since the underlying SQL only aggregates wins/losses. The aggregation SQL lives in `components/rts-data-access/resources/rts-data-access/sql/stats/get-faction-standings-for-{game,league,season}.sql`.

Matches with NULL `player_*_draft_id` (legacy or never-set) simply don't contribute to faction aggregates — they're filtered in the SQL `WHERE` clause.
