# mono

Polylith monorepo for Devereux-Henley applications.

---

## Tools

This repository uses the [Polylith](https://polylith.gitbook.io/polylith) architecture. Polylith organises code into **components** (shared units of behaviour), **bases** (runnable entry points), and **projects** (deployable combinations of the two). The `poly` CLI can be used to inspect the workspace, check component boundaries, and run tests across affected units.

```
clojure -M:poly info
clojure -M:poly check
clojure -M:poly test
```

| Tool | Purpose |
|---|---|
| [Polylith](https://polylith.gitbook.io/polylith) | Workspace structure, dependency validation, incremental testing |
| [Integrant](https://github.com/weavejester/integrant) | Component lifecycle and dependency injection at runtime |
| [Datalevin](https://github.com/juji-io/datalevin) | Embedded LMDB-backed Datalog database for all persistent state |
| [Reitit](https://github.com/metosin/reitit) | HTTP routing with schema coercion and content negotiation |
| [Malli](https://github.com/metosin/malli) | Data schema, validation, and transformation |
| [Selmer](https://github.com/yogthos/Selmer) | HTML templating |
| [Playwright](https://playwright.dev/) | Browser-based e2e testing against the running dev server |

---

## Bases

Bases are runnable entry points. Each base wires together components and library dependencies into a deployable unit.

| Base | Description |
|---|---|
| [`rts-api`](bases/rts-api) | HTTP API and server-rendered UI for the RTS tournament application. Serves JSON, HAL+JSON, and htmx-powered HTML via content negotiation. Runs on Jetty with Reitit routing and Integrant lifecycle management; opens its Datalevin connection at startup. |
| [`rpfm-scraper`](bases/rpfm-scraper) | Scrapes game data (units, factions, abilities, items, mounts) from RPFM and merges it with the curated authoring EDN into the Datalog seed under `components/rts-data/resources`. See [`docs/rpfm-scraper/game-data.md`](docs/rpfm-scraper/game-data.md) and [`docs/rpfm-scraper/edn-seed-pipeline.md`](docs/rpfm-scraper/edn-seed-pipeline.md). |
| [`rts-demo`](bases/rts-demo) | Bootstraps a fresh Datalevin store with demo tournaments (single-elimination, double-elimination, Swiss) advanced to interesting bracket states, so the tournament viewer always has non-trivial data to render. |

---

## Components

Components are shared units of behaviour consumed by one or more bases.

| Component | Description |
|---|---|
| [`rts-web`](components/rts-web) | RTS web layer. Reitit route definitions, Integrant-managed request handlers for API endpoints and server-rendered views, and HTML templates and static assets. |
| [`rts-domain`](components/rts-domain) | RTS domain layer. Handler-level functions that retrieve typed domain models (with `:type` keys) from the data access layer, and Malli schemas for all API resources and request specifications. |
| [`rts-data-access`](components/rts-data-access) | RTS data access layer. Datalog query and mutation functions over the Datalevin store, plus the entity schemas that describe stored data. |
| [`rts-data`](components/rts-data) | RTS Datalog seed. Owns the per-patch EDN seed files under `resources/rts-data/seed/` (produced by `rpfm-scraper`) and the loaders that transact them into the store. |
| [`http`](components/http) | HTTP response helpers used by web handlers: `Either`-based fetch and create pipelines, standard response shaping for collections, single resources, and embedded sub-resources. |
| [`datalog`](components/datalog) | Thin wrapper around Datalevin: connection lifecycle, query and transact helpers. The single seam through which all other units touch the database — domain code never calls `datalevin.core` directly. |
| [`schema`](components/schema) | Shared Malli schema primitives: custom types (`:instant`, `:local-date`, `:url`), base resource and collection schemas, and the model transformer that resolves `:model/link` annotations into HATEOAS `_links` URLs. |
| [`content-negotiation`](components/content-negotiation) | Muuntaja format definitions for `text/html` and `application/htmx+html`. Shared by any base that serves server-rendered HTML alongside JSON. |
| [`resourcekit`](components/resourcekit) | Static CSS assets (reset, tokens, layout, and UI component styles) served by bases that render HTML. |
| [`e2e`](components/e2e) | Playwright e2e tests. Clojure test runner shells out to `npx playwright test`; JavaScript specs cover page navigation, draft UI operations, and HAL+JSON API. See [`docs/rts-api/e2e-testing.md`](docs/rts-api/e2e-testing.md). |

---

## Development

### Toolbox

[`dev-env/`](dev-env) packages a [Fedora Toolbx](https://containertoolbx.org/) image with the full development toolchain (Emacs, JDK 21, Clojure CLI, clj-kondo, cljfmt, gh, sqlite, Node/npm + Playwright deps, Claude Code, [clojure-mcp](https://github.com/bhauman/clojure-mcp)). From `dev-env/`:

```
make build && make create && make enter
```

First interactive enter installs Claude Code and clojure-mcp into the shared home directory and starts the Emacs daemon. See [`CLAUDE.md`](CLAUDE.md#toolbox-dev-environment) for details.

### Claude Code + clojure-mcp

The Toolbox installs [`clojure-mcp`](https://github.com/bhauman/clojure-mcp) and registers it as an MCP server for Claude Code, so Claude drives the running nREPL through structured tools (`clojure_eval`, `clojure_edit`, `paren_repair`, etc.) rather than shelling out. Edits are delimiter-checked and auto-repaired before evaluation, which is why this repo has no separate paren-repair hook configured in `.claude/settings.json`. The canonical Claude workflow — start nREPL on `:7888`, then `(claude-workspace/go!)` over MCP — is described in [`CLAUDE.md`](CLAUDE.md#commands).

### REPL

The repository root `deps.edn` contains a `:dev` alias that wires all components and bases onto the classpath. CIDER is configured via [`.dir-locals.el`](.dir-locals.el) to always jack-in from the repository root using that alias — this ensures the full component graph is available regardless of which file is open.

```
M-x cider-jack-in   ; or C-c C-x j j
```

Approve the `eval` form prompt once (or add `(setq enable-local-eval t)` to your Emacs init to suppress it permanently).

### Database

The development Datalevin store lives at `db/datalevin/` (relative to the repository root, excluded from version control; override with `DATALEVIN_DB_DIR`). The Datalog schema merges into the store when the connection opens, so additive schema changes apply on restart without a migration step.

```clojure
;; From the REPL (helpers in development/src/claude_workspace.clj)
(go!)            ; start system (opens the store, starts Jetty)
(halt!)          ; stop system
(restart!)       ; halt then go!
(seed-datalog!)  ; transact the game-data seed for the default patch (system must be up)
(reset-datalog!) ; halt, wipe the store, and restart against an empty store
```

For a populated tournament UI, run the `rts-demo` base to rebuild the store with demo tournaments.

---

## Documentation

| Document | Description |
|---|---|
| [`docs/api.md`](docs/api.md) | API design: HATEOAS patterns, route structure, handler pipeline, content negotiation, error shapes. |
| [`docs/backend-testing.md`](docs/backend-testing.md) | Testing philosophy: unit tests with stubbed database boundary, domain schema validation, handler transformation tests. |
| [`docs/database.md`](docs/database.md) | Database layer: the Datalevin store, schema merging, EDN seed data, and Datalog conventions. |
| [`docs/frontend.md`](docs/frontend.md) | Frontend patterns: WCAG 2.1 AA accessibility, HTMX conventions, Selmer template structure. |
| [`docs/skins.md`](docs/skins.md) | Per-game visual skins: how a skin is dispatched and how new skins layer palette and component CSS over the default system. |
| [`docs/rts-api/e2e-testing.md`](docs/rts-api/e2e-testing.md) | E2E testing strategy: Playwright architecture, local and CI usage, dev-only endpoints, test categories, adding new specs. |
| [`docs/rpfm-scraper/game-data.md`](docs/rpfm-scraper/game-data.md) | RPFM data refresh workflow: scraping game data after a patch and regenerating the Datalog seed. |
| [`docs/rpfm-scraper/edn-seed-pipeline.md`](docs/rpfm-scraper/edn-seed-pipeline.md) | Seed pipeline: how the curated authoring EDN merges with RPFM output into the per-patch Datalog seed. |

External references: [Polylith documentation](https://polylith.gitbook.io/polylith) · [Polylith tool](https://github.com/polyfy/polylith)
