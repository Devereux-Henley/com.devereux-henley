# Flow: Game Browsing

Covers navigating from the game selector through a game's home, factions list, faction detail, and unit rosters. Tested by `components/e2e/tests/game-browsing.spec.js`.

## Game index

Selecting a game from the game selector at `/view/game/index.html` navigates to that game's home at `/view/game/:game-eid/index.html`. The navbar brand cluster picks up the active game's logo and name, and the game-scoped nav items (Atlas, My Drafts, Tournaments) become active links. If the game is configured with a skin (see `docs/skins.md`), the Warhammer-style theme CSS loads on this and every other route under `/view/game/:game-eid/`.

![Game index](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/game-index.png)

## Factions list

The Atlas dropdown in the navbar exposes a single **Factions** entry, which navigates to `/view/game/:game-eid/faction/index.html`. The page renders a card grid of every faction for the active game, each card linking to its detail page.

![Factions list](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/game-factions-list.png)

## Faction page

The faction page shows the faction name and a table of units grouped by category (Lord, Hero, Infantry, Cavalry, etc.). Each unit row links to the full unit detail page.

![Faction page](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/game-faction-page.png)
