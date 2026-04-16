# Flow: Game Browsing

Covers navigating from the game index through factions and unit rosters. Tested by `components/e2e/tests/game-browsing.spec.js`.

## Game index

Selecting a game from the Games dropdown navigates to the game index page. The navbar populates with game-context items: the Factions dropdown, My Drafts link, and Tournaments link.

![Game index](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/game-index.png)

## Factions dropdown

The Factions dropdown lists all factions for the selected game. Each entry navigates to the faction detail page with its unit roster.

![Factions dropdown](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/game-factions-dropdown.png)

## Faction page

The faction page shows the faction name and a table of units grouped by category (Lord, Hero, Infantry, Cavalry, etc.). Each unit row links to the full unit detail page.

![Faction page](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/game-faction-page.png)
