# Flow: Tournament Views

Covers the tournament create + detail UI pages. The standalone tournament list page has been replaced by the **Competitive** landing — see [`competitive.md`](competitive.md) for the league/season-aware tournament list and the league/season pages above tournaments.

## Create tournament

Form with fieldset-grouped sections: tournament details, optional **League** picker (standalone vs. attach to a league/season), registration window, and optional phase configuration. The page wrapper is `overflow-y: auto` with bottom padding so the Create Tournament submit button keeps a minimum distance from the viewport bottom as the phase configurator grows (one row added per "+ Add Phase" click).

When a league is chosen, an `hx-get` swaps a season `<select>` into a placeholder `<div>` so the user can also pick a season. Selecting a season populates `:season-eid` in the create payload — the domain layer derives `:league-eid` from the season server-side.

![Create tournament with league dropdown](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-create-tournament-league-dropdown.png)

## Tournament detail — tab-based layout

The detail view uses a persistent sidebar plus a tabbed main panel:

- **Sidebar** (always visible): registration window, "Your Entry" action, **My Matches — Draft Selection** card (only when the current user is a participant in any match — see [`competitive.md`](competitive.md)), phase overview, and organizer controls.
- **Tabs** across the top of the main panel — **Entrants** first, then one tab per configured phase (labelled `Phase N` with the phase type as a subtitle).
- **League badge** in the hero (when the tournament is attached to a league) — links to the league detail page.

The Entrants tab ships its content server-rendered on initial load. Phase tabs start empty; the first click on a phase tab fires `GET /api/tournament/:eid/phase/:phase-index` and swaps the response into the panel. Subsequent clicks refetch — the response shape is a typed map dispatched through the `application/htmx+html` view-fn to `rts-web/resource/tournament-phase-panel.html`, which reuses the same per-phase-type partials the initial page-render uses (`phase-single-elimination.html`, `phase-double-elimination.html`, `phase-swiss.html`, `phase-round-robin.html`). Standings (`_standings.html`) render inside the phase panel for Swiss and round-robin only — elimination phases have no meaningful points ladder.

![Registration detail](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-registration.png)

## Active Swiss tournament

Swiss and round-robin tabs render their Standings table above the match list. The round header shows `Round X of Y`. Completed match cards get a green left border and the winner's name highlighted in gold.

![Swiss with results](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-active.png)

## Swiss round layout

Each round block keeps `Round N of M` counter labels so organisers can see overall phase progress. Pending matches show a dim status badge; byes render as an italicised placeholder opposite the paired player.

![Swiss rounds](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-swiss-rounds.png)

## Single elimination bracket

Single-elim phases render as a horizontal bracket of round columns. Round labels (`Round 1`, `Round 2`, …) are pinned to the top of every column so they line up across the bracket regardless of how many matches each round contains. The full skeleton is pre-rendered from the phase config — future rounds show TBD placeholder slots (dashed borders, dimmed) until matches are generated. Completed matches highlight the winner.

![Single elimination bracket](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/bracket-single-elim.png)

## Double elimination bracket

Double elim renders as a single combined layout under one **Double Elimination** heading:

- The **Winners Bracket** row sits above the **Losers Bracket** row in a shared column, so the two brackets read as parallel paths through the phase.
- The **Grand Final** panel sits to the right of the brackets, vertically centred on the full phase height. Two dashed `}`-shaped connector arms visualise the last WB match and the last LB match feeding into the Grand Final.
- Round titles are aligned across all columns; the GF column is tinted with the primary accent colour to distinguish it from a regular round.

The Losers Bracket is a single-elimination tree seeded by WB round-1 losers only: for a 16-player bracket that's 3 LB rounds (4 → 2 → 1), exactly half of the Winners Bracket. The minor/major "merge" rounds that previously padded out the bracket to 2×(WB-1) rounds have been removed — every LB round now represents a genuine elimination step.

`POST /api/tournament/:eid/round` still generates every dependency-satisfied round across all brackets in one call, so one click may emit a new WB round, an LB round, or the Grand Final.

![Double elimination bracket](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/bracket-double-elim.png)
