# Flow: Navigation

Covers the game selector, navbar structure, and the account menu. Tested by `components/e2e/tests/navigation.spec.js`.

## Game selector

The root URL (`/`) redirects to `/view/game/index.html`. The game selector is the landing page — a card grid of every game registered in the system. Each card is a link to that game's home at `/view/game/:game-eid/index.html`.

![Game selector](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/nav-game-selector.png)

## Navbar

The navbar is emitted by `rts-web/template/entrypoint.html` and has three regions:

1. **Brand cluster** (`.navbar-brand-cluster`) — the RTS-Hub wordmark + an optional game indicator (the active game's logo and name), separated by a hairline divider. Clicking the wordmark returns to `/`. Clicking the game indicator navigates to that game's home.
2. **Main nav** — Atlas (dropdown), My Drafts (game-scoped), Competitive (game-scoped). The per-slot widths are pinned in CSS so the row doesn't reflow when a game enters or leaves context.
3. **Account menu** — game socials cluster + the signed-in user's name with a logout affordance.

The `base-context` helper in `web/view.clj` threads an `:active-nav` keyword (`"atlas"` / `"drafts"` / `"competitive"` / nil) derived from the request URI. Nav items check it and attach `class="nav-item active"` so CSS can draw the terracotta underline on whichever top-level is currently in view. The `competitive` key matches any URI containing `/competitive`, `/league`, `/season`, or `/tournament` — so navigating into a tournament detail (or its underlying league/season) keeps the **Competitive** tab highlighted.

![Navbar with Competitive link](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/nav-competitive.png)

All navbar links use standard browser navigation. HTMX's `hx-boost` is deliberately disabled — letting the browser re-render `<head>` on every navigation is what allows the skin system (see `docs/skins.md`) to swap theme `<link>` tags cleanly when a user moves between a themed game's routes and un-themed routes.

## Atlas dropdown

Atlas is a dropdown button (book icon). It currently exposes a single option, **Factions** (sword icon), which navigates to `/view/game/:game-eid/faction/index.html` — a card grid of every faction in the active game. Clicking a faction card loads its unit roster at `/view/game/:game-eid/faction/:eid/index.html`.

![Atlas dropdown](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/nav-atlas-dropdown.png)

## Account menu

The account menu shows the authenticated user's name and a logout link. In the development profile, the user is determined by the `dev_impersonation` cookie.

![Account menu](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/nav-account-menu.png)
