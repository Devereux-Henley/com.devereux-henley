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
| [Migratus](https://github.com/yogthos/migratus) | SQL database migrations with versioned up/down files |
| [Reitit](https://github.com/metosin/reitit) | HTTP routing with schema coercion and content negotiation |
| [Malli](https://github.com/metosin/malli) | Data schema, validation, and transformation |
| [next.jdbc](https://github.com/seancorfield/next-jdbc) | Database access |
| [Selmer](https://github.com/yogthos/Selmer) | HTML templating |
| [Playwright](https://playwright.dev/) | Browser-based e2e testing against the running dev server |

---

## Bases

Bases are runnable entry points. Each base wires together components and library dependencies into a deployable unit.

| Base | Description |
|---|---|
| [`rts-api`](bases/rts-api) | HTTP API and server-rendered UI for the RTS tournament application. Serves JSON, HAL+JSON, and htmx-powered HTML via content negotiation. Runs on Jetty with Reitit routing and Integrant lifecycle management. Applies database migrations on startup. |
| [`rts-data-deploy`](bases/rts-data-deploy) | CLI tool for running RTS database migrations in CI, independent of the application. Accepts `migrate` or `rollback` as a command-line argument. |
| [`rpfm-scraper`](bases/rpfm-scraper) | Scrapes game data (units, factions, abilities, items, mounts) from RPFM and generates seed SQL files. See [`docs/game-data.md`](docs/game-data.md). |

---

## Components

Components are shared units of behaviour consumed by one or more bases.

| Component | Description |
|---|---|
| [`rts-web`](components/rts-web) | RTS web layer. Reitit route definitions, Integrant-managed request handlers for API endpoints and server-rendered views, and HTML templates and static assets. |
| [`rts-domain`](components/rts-domain) | RTS domain layer. Handler-level functions that retrieve typed domain models (with `:type` keys) from the data access layer, and Malli schemas for all API resources and request specifications. |
| [`rts-data-access`](components/rts-data-access) | RTS data access layer. SQL query functions backed by named `.sql` files, JDBC entity schemas, and all database read/write operations. |
| [`rts-data`](components/rts-data) | RTS database schema. Owns the numbered Migratus migration SQL files and the Integrant `::migrate` key that applies them. |
| [`http`](components/http) | HTTP response helpers used by web handlers: `Either`-based fetch and create pipelines, standard response shaping for collections, single resources, and embedded sub-resources. |
| [`jdbc`](components/jdbc) | JDBC query helpers: camel-snake-kebab column mapping, `query-for-entity`, `query-for-entities`, `entity-by-eid`, `insert!`, and `execute-one!` wrappers over `next.jdbc`. |
| [`schema`](components/schema) | Shared Malli schema primitives: custom types (`:instant`, `:local-date`, `:url`), base resource and collection schemas, and the model transformer that resolves `:model/link` annotations into HATEOAS `_links` URLs. |
| [`content-negotiation`](components/content-negotiation) | Muuntaja format definitions for `text/html` and `application/htmx+html`. Shared by any base that serves server-rendered HTML alongside JSON. |
| [`resourcekit`](components/resourcekit) | Static CSS assets (reset, tokens, layout, and UI component styles) served by bases that render HTML. |
| [`e2e`](components/e2e) | Playwright e2e tests. Clojure test runner shells out to `npx playwright test`; JavaScript specs cover page navigation, draft UI operations, and HAL+JSON API. See [`docs/e2e-testing.md`](docs/e2e-testing.md). |

---

## Development

### REPL

The repository root `deps.edn` contains a `:dev` alias that wires all components and bases onto the classpath. CIDER is configured via [`.dir-locals.el`](.dir-locals.el) to always jack-in from the repository root using that alias — this ensures the full component graph is available regardless of which file is open.

```
M-x cider-jack-in   ; or C-c C-x j j
```

Approve the `eval` form prompt once (or add `(setq enable-local-eval t)` to your Emacs init to suppress it permanently).

### Database

The SQLite development database lives at `db/database.db` (relative to the repository root, excluded from version control). Migrations run automatically on `(go!)`. To apply migrations independently:

```clojure
;; From the REPL
(go!)      ; start system (runs migrations, starts Jetty)
(halt!)    ; stop system
(restart!) ; halt then go!
```

---

## Documentation

| Document | Description |
|---|---|
| [`docs/api.md`](docs/api.md) | API design: HATEOAS patterns, route structure, handler pipeline, content negotiation, error shapes. |
| [`docs/backend-testing.md`](docs/backend-testing.md) | Testing philosophy: unit tests with stubbed database boundary, domain schema validation, handler transformation tests. |
| [`docs/database.md`](docs/database.md) | Database migration strategy: Migratus setup, migration ordering, how migrations run in the application and in CI, and the migration test approach. |
| [`docs/e2e-testing.md`](docs/e2e-testing.md) | E2E testing strategy: Playwright architecture, local and CI usage, dev-only endpoints, test categories, adding new specs. |
| [`docs/frontend.md`](docs/frontend.md) | Frontend patterns: WCAG 2.1 AA accessibility, HTMX conventions, Selmer template structure. |
| [`docs/game-data.md`](docs/game-data.md) | RPFM data refresh workflow: scraping game data after a patch and regenerating seed SQL files. |

External references: [Polylith documentation](https://polylith.gitbook.io/polylith) · [Polylith tool](https://github.com/polyfy/polylith)
