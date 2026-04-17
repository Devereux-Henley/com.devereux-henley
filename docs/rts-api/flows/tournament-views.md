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
