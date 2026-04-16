# E2E Testing

## Overview

End-to-end tests use [Playwright](https://playwright.dev/) to exercise the running application through a real browser (Chromium) and the HTTP API. They complement the unit tests documented in `docs/backend-testing.md` by covering the layers that unit tests intentionally skip: routes, middleware, content negotiation, Selmer templates, HTMX interactions, and the full request lifecycle.

The tests live in the `e2e` Polylith component (`components/e2e/`) and are part of the `api` project, so `clojure -M:poly test` discovers and runs them alongside the unit tests.

---

## Architecture

```
components/e2e/
├── src/com/devereux_henley/e2e/contract.clj   # Clojure helpers: server check, Playwright runner, shutdown
├── test/com/devereux_henley/e2e/playwright_test.clj  # clojure.test entry point
├── tests/                                     # Playwright specs (JavaScript)
│   ├── navigation.spec.js                     # Static pages, nav dropdowns, redirects
│   ├── game-browsing.spec.js                  # Game/faction/unit page navigation
│   ├── draft.spec.js                          # Authenticated page loads (create draft, my drafts)
│   ├── draft-operations.spec.js               # UI draft flow: select, add, update, remove, validation
│   └── draft-api.spec.js                      # HAL+JSON API: CRUD, _links, _embedded, validation
├── playwright.config.js                       # Chromium-only, targets localhost:3001
├── package.json                               # @playwright/test dependency
└── resources/e2e/start_dev_server.clj         # Boots dev Integrant system + seeds DB
```

A single `deftest` in `playwright_test.clj` checks two preconditions, then shells out to `npx playwright test`:

1. **Playwright installed?** — checks for `node_modules/` in the e2e component directory
2. **Server reachable?** — HTTP GET to `/status`

If either check fails:
- **Locally** (`CI` env var unset): prints a SKIP message; the test passes vacuously (0 assertions)
- **In CI** (`CI=true`, set automatically by GitHub Actions): fails with `(is false ...)` so the workflow never silently skips

---

## Running locally

```bash
# One-time setup
cd components/e2e && npm install && npx playwright install chromium && cd ../..

# Start the dev server (migrations + seed + Jetty on :3001)
mkdir -p db
clojure -M:dev -i components/e2e/resources/e2e/start_dev_server.clj &
until curl -sf http://localhost:3001/status > /dev/null 2>&1; do sleep 2; done

# Run all tests (unit + e2e)
clojure -M:poly test

# Or run Playwright directly for faster iteration
cd components/e2e && npx playwright test

# Shut down the dev server
curl -X POST http://localhost:3001/shutdown
```

Without the server running, `poly test` still passes — the e2e tests skip with a message.

---

## CI integration

Both GitHub Actions workflows (`pr-check.yml`, `tag-stable.yml`) run e2e tests as part of the single `poly test` step:

1. **Set up Node.js** (v22) and install Playwright + Chromium
2. **Start dev server** in background using `start_dev_server.clj`, poll `/status` until ready
3. **`clojure -M:poly test`** — runs unit tests and e2e tests together
4. **`POST /shutdown`** (`if: always()`) — cleanly halts the Integrant system

The `CI=true` env var (set by GitHub Actions) ensures the test fails hard if Playwright or the server aren't available.

---

## Dev-only endpoints

Two endpoints support the e2e workflow. Neither is wired in `core-configuration` (production).

| Endpoint | Profile | Purpose |
|---|---|---|
| `GET /status` | Both | Returns `{"status":"ok"}`. Used for readiness checks. |
| `POST /shutdown` | Dev only | Returns `{"status":"shutting-down"}`, then halts the JVM after 100ms. Triggers the shutdown hook which calls `ig/halt!`. |

---

## Test categories

### Page navigation (`navigation.spec.js`)

Loads static pages (dashboard, about, contact), verifies titles, nav elements, redirect from `/`, games dropdown content, and account menu presence.

### Game browsing (`game-browsing.spec.js`)

Navigates game index, faction, and unit pages. Verifies factions dropdown populates and faction links navigate correctly.

### Draft page loads (`draft.spec.js`)

Sets the `dev_impersonation` cookie, verifies authenticated UI elements (account menu, "My Drafts" link), and that the create-draft and my-drafts pages render.

### Draft UI operations (`draft-operations.spec.js`)

Full browser interaction with the draft builder using HTMX:

- Select a unit from the roster (click card, verify stats panel)
- Add a lord to the main army section (verify lord slot fills)
- Add infantry to main and reinforcements sections
- Update placed unit selections (toggle spell/item checkbox, verify cost change via OOB swap)
- Remove a unit (hover slot, click remove button, verify slot disappears)
- Lord cap validation (add two lords, verify error)
- Per-unit cap validation (add 5 copies, verify error)
- Budget display updates after adding units

### Draft API (`draft-api.spec.js`)

Exercises the JSON API directly using Playwright's `request` context with `Accept: application/hal+json`:

- Create draft (`PUT`), verify `_links.self`, `player-sub`
- Get draft unit (`GET`), verify statistics array and `_links.draft`
- Add units to main and reinforcements (`POST`), verify budget tracking
- Get draft entry with `embed=unit` (`GET`), verify `_embedded.unit`
- Update selections (`PATCH`), verify cost change
- Remove unit (`DELETE`), verify `removed-is-lord` flag and budget reset
- Lord cap: second lord returns 422 with `draft/add-error`
- Per-unit cap: 5th copy returns 422 with error message
- Budget accumulation across multiple adds

---

## Adding a new e2e test

1. Create a `.spec.js` file in `components/e2e/tests/`
2. Use `@playwright/test` imports (`test`, `expect`)
3. For authenticated tests, set the `dev_impersonation` cookie in `beforeEach`
4. For API tests, use `request.get/post/put/patch/delete` with `Accept: application/hal+json`
5. Run `npx playwright test tests/your-file.spec.js` from `components/e2e/` to iterate

No Clojure changes are needed — the test runner discovers all `.spec.js` files automatically via the Playwright config's `testDir: './tests'`.
