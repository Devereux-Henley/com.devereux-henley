# Flow: Tournament Views

Covers the tournament UI pages — list, create, and detail views.

## Tournament list

Card-based grid showing all tournaments for the current game. Each card displays the tournament name, status badge (color-coded by state), description, entry count, and creator. Cards link to the tournament detail page.

![Tournament list](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-list.png)

## Create tournament

Form with fieldset-grouped sections: tournament details (name, description), registration window (timezone, open/close dates), and optional phase configuration. The phase configurator supports adding multiple phases with type selection (swiss, round-robin, single-elimination, double-elimination) and per-round format (bo1/bo3/bo5).

![Create tournament](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-create.png)

## Tournament detail — registration

Dashboard layout with sidebar and main content. The sidebar shows registration window info, player entry action (enter/withdraw), configured phases with active phase highlighted, and organizer controls. The main area shows entries as chips.

![Registration detail](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-registration.png)

## Tournament detail — active with matches

When a tournament is active, the main area shows the standings table (ranked by points) and match cards grouped by phase and round. Completed matches have a green left border and the winner's name is highlighted in gold. The organizer sidebar shows "Generate Next Round", "Complete Tournament", and "Cancel Tournament" buttons.

![Active tournament](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-active.png)

## Swiss round counter

Swiss and round-robin phases display "Round X of Y" in the round header, showing progress through the phase. Each phase gets its own section heading with the phase type name.

![Swiss rounds](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/views-swiss-rounds.png)

## Single elimination bracket

Single elimination phases render as a horizontal bracket with round columns. The full bracket skeleton is pre-rendered from the phase config — future rounds show TBD placeholder slots (dashed borders, dimmed) until matches are generated. Completed matches show the winner highlighted in gold.

![Single elimination bracket](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/bracket-single-elim.png)

## Double elimination bracket

Double elimination renders as three stacked sections under a shared "Double Elimination" heading: **Winners Bracket**, **Losers Bracket**, and **Grand Final**. Each section reuses the round-column bracket layout, with TBD placeholders for rounds that haven't been generated yet. Match losers in the winners bracket feed into the losers bracket; the losers bracket champion meets the winners bracket champion in a single grand-final match (no bracket reset). Round labels are prefixed with `WB` or `LB` so it's clear which bracket each column belongs to.

`POST /api/tournament/:eid/round` generates every dependency-satisfied round across all three brackets in a single call — so one click may produce a new winners round, a new losers round, or both. The tournament auto-completes when the grand-final match resolves.

![Double elimination bracket](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/bracket-double-elim.png)
