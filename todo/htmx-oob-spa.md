# Feature: HTMX OOB Swap SPA Shell

## Goal

Convert the app from full-page Selmer template renders per navigation to a single-shell architecture where:

- `entrypoint.html` loads **once** as a persistent shell containing the nav and an empty `#content` area.
- Subsequent navigations issue HTMX `hx-get` requests to existing API endpoints requesting `application/htmx+html`.
- API endpoints return HTML fragments that OOB-swap into `#content` and any nav sub-regions that need updating (`#game-nav`, `#game-icon`, `#game-socials`).
- The browser URL is updated via `hx-push-url` so deep links and refresh still work.

The `/view/...` routes may still exist for initial direct-URL loads (see questions), but they no longer own layout — they either return the full shell (with content injected server-side) or redirect into the shell with an HTMX trigger.

---

## Current Architecture

```
GET /view/faction/:eid/index.html
  → view handler runs pipeline, calls selmer/render-file "faction.html"
  → faction.html {% extends "entrypoint.html" %}
  → browser receives full HTML page
```

All view handlers use either `standard-view-handler` or `standard-entity-view-handler`, both calling `selmer.parser/render-file` with a template that extends `entrypoint.html`. Full page on every navigation.

The API routes already support `application/htmx+html` via Muuntaja content negotiation. The `<body>` element already sends `hx-headers='{"Accept": "application/htmx+html"}'` globally.

---

## Target Architecture

```
Initial load: GET /view/faction/:eid/index.html (or / → shell)
  → server returns entrypoint shell with #content empty (or pre-populated)

Navigation: user clicks faction link (hx-get="/api/game/faction/:eid" hx-target="#content")
  → HTMX GET /api/game/faction/:eid  Accept: application/htmx+html
  → server returns multi-part OOB fragment:
      <section>...faction content...</section>         ← swapped into #content
      <div id="game-nav" hx-swap-oob="true">...</div> ← replaces game nav
      <div id="game-icon" hx-swap-oob="true">...</div>
  → HTMX swaps #content, updates nav, pushes URL
```

---

## Required Changes

### 1. entrypoint.html — become a true shell

- Remove `{% block content %}` and `{% block header %}` inheritance blocks.
- Add `id="content"` to `<main class="app-content">` as the primary swap target.
- `#game-nav`, `#game-icon`, `#game-socials` already have IDs — make them stable OOB targets.
- The shell renders **without** per-page context (no `game-eid`, `factions`, etc.) on initial load; nav context arrives via OOB on first content load.

```html
<main class="app-content" id="content">
    <!-- populated by HTMX OOB swap -->
</main>
```

### 2. Partial fragment templates

For each existing view template, extract the `{% block content %}` body into a **partial** template that does not extend `entrypoint.html`:

| Current full-page template | New partial template |
|---|---|
| `view/faction.html` | `partial/faction.html` |
| `view/unit.html` | `partial/unit.html` |
| `view/game-index.html` | `partial/game-index.html` |
| `view/my-drafts.html` | `partial/my-drafts.html` |
| `view/create-draft.html` | `partial/create-draft.html` |
| `view/draft-index.html` | `partial/draft-index.html` |
| `view/dashboard.html` | `partial/dashboard.html` |

Each partial renders only the `<section>` content, plus appended OOB swap elements for nav.

### 3. OOB nav fragment template

A shared `partial/nav-context.html` partial that renders the nav OOB targets given context variables:

```html
<div id="game-nav" hx-swap-oob="true">
    {% if factions %}
    <div class="dropdown">...</div>
    {% endif %}
</div>
<div id="game-icon" hx-swap-oob="true">...</div>
```

Each content partial `{% include %}` this at the bottom, passing `game-eid` and `factions`. Resources that have no game context (dashboard, about, contact) include it with empty values to clear the nav.

### 4. `application/htmx+html` response encoding

The Muuntaja encoder for `application/htmx+html` is what the API endpoints call when HTMX requests the route. Currently in the `rts-api` base there is a dispatch map from resource `:type` keyword to a Selmer template name (in `web.clj`). This dispatch map needs updating:

- Add entries pointing to **partial** templates instead of (or in addition to) the full-page templates.
- The encoder renders the partial + OOB nav fragment and returns the combined HTML string.

> **Open question:** Is the htmx+html encoder already rendering a Selmer template per resource type? Read `web.clj` in the base to confirm the dispatch map structure before implementing.

### 5. Navigation links — switch to HTMX attributes

All `<a href="...">` links that navigate to view routes must become HTMX-driven:

```html
<!-- before -->
<a href="/view/faction/{{faction.eid}}/index.html">{{faction.name}}</a>

<!-- after -->
<a hx-get="/api/game/faction/{{faction.eid}}"
   hx-target="#content"
   hx-push-url="/view/faction/{{faction.eid}}/index.html"
   href="/view/faction/{{faction.eid}}/index.html">
   {{faction.name}}
</a>
```

`href` is retained as progressive-enhancement fallback and for right-click → open in new tab.

`hx-push-url` points to the **view URL** (not the API URL) so the browser history stays human-readable and deep-linkable.

Affected: faction dropdown in `#game-nav`, game dropdown, Drafts link, Create Draft button, draft list links in `my-drafts`, unit table links in `faction.html`.

### 6. View routes — initial load behaviour

Two options for handling direct URL access (e.g. user refreshes `/view/faction/:eid/index.html`):

**Option A — View routes return full shell with pre-populated content**
View handlers render `entrypoint.html` directly (not via `{% extends %}`) with the content area already populated. This duplicates some rendering logic.

**Option B — View routes return shell + auto-trigger HTMX load**
View routes return the empty shell and embed a one-shot HTMX trigger:
```html
<div hx-get="/api/game/faction/{{eid}}" hx-trigger="load" hx-target="#content"></div>
```
Simpler to implement; adds one extra round-trip on refresh.

**Option C — View routes 301 → shell root, shell reads URL and dispatches**
Most complex; requires client-side routing logic.

> **Open question:** Which option is preferred? Option B is the lowest implementation cost. Option A gives better perceived performance on initial load (no extra round trip) and is better for SEO/crawlers.

### 7. Per-page stylesheets

Currently some views have a `{% block styles %}` that loads `dashboard.css`. In the shell model the shell `<head>` is fixed. Options:

- **Include all stylesheets in the shell** — simplest, small overhead.
- **OOB swap a `#page-styles` placeholder in `<head>`** — HTMX does not support swapping into `<head>` natively; requires a workaround (`hx-swap-oob` with `outerHTML` on a `<style>` tag, or a hyperscript that appends a `<link>`).

> **Open question:** Is `dashboard.css` the only per-page stylesheet? If all views share `app.css` + `dashboard.css`, just load both in the shell and drop `{% block styles %}`.

---

## Data Flow for OOB Nav Context

The nav requires `game-eid`, `factions`, and `session` as template context. In the current full-page model these come from the view handler's `extra-data-fn`. In the OOB model:

- `session` is available in every handler via `:ory-session` on the request — no change.
- `game-eid` and `factions` come from the entity being rendered (e.g. a faction resource knows its `game-eid`; a game resource has `_embedded.factions`).
- For resources with no game context (dashboard, tournament list), the OOB nav fragment is rendered with empty values, clearing the game-specific nav items.

The `htmx+html` encoder therefore needs access to the same context that `extra-data-fn` currently produces. The cleanest approach is to pass nav context alongside the resource model into the encoder, rather than fetching it again.

> **Open question:** Should the htmx+html OOB response always re-render the full nav fragment (including the drafts link, profile dropdown), or only the game-specific sub-section (`#game-nav`)? Rendering the full nav on every response keeps things stateless but increases fragment size.

---

## Error and Loading States

- HTMX `hx-indicator` on each nav link can show a loading spinner inside `#content` while the swap is in flight.
- Error responses (404, 500) from the API endpoint should return an OOB fragment with an error message in `#content` rather than a bare error body.

> **Open question:** Should error fragments also OOB-update the nav (e.g. clear `#game-nav` on 404), or leave nav in its last known state?

---

## Affected Files Summary

| File | Change |
|---|---|
| `entrypoint.html` | Remove block inheritance; add `id="content"` to `<main>`; stable IDs on nav targets |
| `view/*.html` | Keep for direct-load fallback (full shell); content extracted into `partial/*.html` |
| `partial/*.html` | New — content fragments + OOB nav include |
| `partial/nav-context.html` | New — shared OOB nav targets |
| `web.clj` (rts-api base) | Update htmx+html dispatch map to use partial templates |
| `view.clj` (rts-web) | Update `standard-entity-view-handler` to support partial rendering path |
| All template links | Add `hx-get`, `hx-target="#content"`, `hx-push-url` attributes |

---

## Open Questions Summary

1. **Option A, B, or C for direct URL / refresh handling?** Option B (auto-trigger on load) is cheapest; Option A (pre-populated shell) is better UX.
2. **Is `dashboard.css` the only per-page stylesheet?** If yes, load all CSS in the shell and eliminate `{% block styles %}`.
3. **Does the htmx+html encoder already use a dispatch map in `web.clj`?** Confirm before designing the partial template dispatch.
4. **Should the full nav fragment or only `#game-nav` be OOB-swapped on each navigation?**
5. **Should error responses OOB-clear the nav, or leave it unchanged?**
6. **Forms (create-draft submit) use `fetch()` JS today, not HTMX. Should these be converted to `hx-put` / `hx-post` with OOB response, or left as JS fetch with a redirect?**
