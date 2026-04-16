# Flow: Navigation

Covers static pages, the games dropdown, and the account menu. Tested by `components/e2e/tests/navigation.spec.js`.

## Dashboard

The root URL (`/`) redirects to `/view/dashboard.html`. The dashboard is the landing page — it shows the navbar with the Games dropdown and the account menu.

![Dashboard](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/nav-dashboard.png)

## Games dropdown

Clicking "Games" in the navbar opens a dropdown listing all available games. Each entry is a link that navigates to the game index page and populates the game-context navbar items (Factions, My Drafts, Tournaments).

![Games dropdown](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/nav-games-dropdown.png)

## Account menu

The account menu shows the authenticated user's name and a logout link. In the development profile, the user is determined by the `dev_impersonation` cookie.

![Account menu](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/flows/rts-api/nav-account-menu.png)
