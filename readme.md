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

---

## Bases

Bases are runnable entry points. Each base wires together components and library dependencies into a deployable unit.

| Base | Description |
|---|---|
| [`rts-api`](bases/rts-api) | HTTP API and server-rendered UI for the RTS tournament application. Serves JSON, HAL+JSON, and htmx-powered HTML via content negotiation. Runs on Jetty with Reitit routing and Integrant lifecycle management. Applies database migrations on startup. |
| [`rts-data-deploy`](bases/rts-data-deploy) | CLI tool for running RTS database migrations in CI, independent of the application. Accepts `migrate` or `rollback` as a command-line argument. |
| [`rose-api`](bases/rose-api) | Minimal reference API. Models the same patterns as `rts-api` at smaller scale. |

---

## Components

Components are shared units of behaviour consumed by one or more bases.

| Component | Description |
|---|---|
| [`rts-data`](components/rts-data) | RTS database schema. Owns the numbered Migratus migration SQL files and the Integrant `::migrate` key that applies them. |
| [`schema`](components/schema) | Shared Malli schema primitives: custom types (`:instant`, `:local-date`, `:url`), base resource and collection schemas, and the model transformer that resolves `:model/link` annotations into HATEOAS `_links` URLs. |
| [`content-negotiation`](components/content-negotiation) | Muuntaja format definitions for `text/html` and `application/htmx+html`. Shared by any base that serves server-rendered HTML alongside JSON. |
| [`resourcekit`](components/resourcekit) | Static CSS assets (reset, tokens, layout, and UI component styles) served by bases that render HTML. |

---

## Documentation

| Document | Description |
|---|---|
| [`docs/api.md`](docs/api.md) | API design: HATEOAS patterns, route structure, handler pipeline, content negotiation, error shapes. |
| [`docs/backend-testing.md`](docs/backend-testing.md) | Testing philosophy: unit tests with stubbed database boundary, domain schema validation, handler transformation tests. |
| [`docs/database.md`](docs/database.md) | Database migration strategy: Migratus setup, migration ordering, how migrations run in the application and in CI, and the migration test approach. |

External references: [Polylith documentation](https://polylith.gitbook.io/polylith) · [Polylith tool](https://github.com/polyfy/polylith)
