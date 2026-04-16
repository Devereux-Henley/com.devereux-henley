# rts-api

The primary API base for the RTS tournament platform. Serves the HAL+JSON API and server-rendered htmx views for game browsing, army drafting, and tournament management.

## Sub-documents

- [E2E Testing](rts-api/e2e-testing.md) — Playwright test setup, CI integration, test categories
- [Tournament Plan](rts-api/tournament-plan.md) — MVP implementation plan for the tournament feature
- [Content Negotiation & HATEOAS](rts-api/content-negotiation-hateoas.md) — rts-api-specific view-by-type dispatch, template routing, and error rendering

## Flows

Visual walkthroughs of each e2e-tested user flow, with annotated screenshots.

- [Navigation](rts-api/flows/navigation.md) — dashboard, games dropdown, account menu
- [Game Browsing](rts-api/flows/game-browsing.md) — game index, factions dropdown, faction page
- [Draft Operations](rts-api/flows/draft-operations.md) — create, unit selection, add/remove/edit, validation
- [Tournament Registration](rts-api/flows/tournament-registration.md) — register, withdraw, validation rules

## Key paths

| Path | Purpose |
|------|---------|
| `bases/rts-api/src/.../configuration.clj` | Integrant system map, per-namespace handler wiring |
| `bases/rts-api/src/.../web.clj` | Middleware stack, exception handlers, view-by-type dispatch |
| `bases/rts-api/src/.../dev_auth.clj` | Dev-only cookie-based impersonation |
| `components/rts-web/src/.../web/routes.clj` | Reitit route definitions |
| `components/rts-web/resources/rts-web/view/` | Selmer HTML templates |

## Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `RTS_API_PORT` | `3001` | HTTP listen port |
| `RTS_API_HOSTNAME` | `http://localhost:3001` | Base URL for link generation |
| `AUTH_HOSTNAME` | `http://localhost:4000` | Ory auth service base URL |
| `AUTH_SLUG` | — | Ory tenant slug |

## Authentication

Session authentication is handled by `ory-session-middleware`, which wraps the Ring handler stack. On each request it looks for a session cookie, fetches session data from Ory's `/sessions/whoami` endpoint, and attaches it to the request under `:ory-session`. Handlers and templates read identity information from this key.

The development profile swaps Ory for a cookie-based impersonation stub (`dev_auth.clj`). Set the `dev_impersonation` cookie to `dev-admin`, `dev-player-one`, or `dev-player-two`.

## Dev-only endpoints

| Endpoint | Profile | Purpose |
|----------|---------|---------|
| `GET /status` | Both | Returns `{"status":"ok"}`. Used for readiness checks. |
| `POST /shutdown` | Dev only | Halts the JVM after 100ms. Used by e2e tests and Claude's REPL workflow. |
