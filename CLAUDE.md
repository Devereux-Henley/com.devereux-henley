# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Clojure Parenthesis Repair

The command `clj-paren-repair` is installed on your path.

Examples:
`clj-paren-repair <files>`
`clj-paren-repair path/to/file1.clj path/to/file2.clj path/to/file3.clj`

**IMPORTANT:** Do NOT try to manually repair parenthesis errors.
If you encounter unbalanced delimiters, run `clj-paren-repair` on the file
instead of attempting to fix them yourself. If the tool doesn't work,
report to the user that they need to fix the delimiter error manually.

The tool automatically formats files with cljfmt when it processes them.

## Commands

```bash
# Workspace health
clojure -M:poly info      # show components, bases, projects
clojure -M:poly check     # validate component boundaries
clojure -M:poly test      # run tests across affected units

# Build JARs
clojure -M:build -A api uber          # → target/rts-api.jar
clojure -M:build -A deploy-data uber  # → migrations CLI JAR

# Docker (from bases/rts-api/)
make build_docker
make run_docker
make ory_local            # create Ory Cloud tunnel for local auth

# Refresh game data seed files from RPFM (after a patch)
python3 scripts/update_from_rpfm.py --data-dir scripts/rpfm_data
```

See `docs/game-data.md` for the full RPFM data refresh workflow.

**REPL (primary dev workflow):** Jack-in from the repo root with `M-x cider-jack-in`. The `:dev` alias (configured in `.dir-locals.el`) puts all components and bases on the classpath.

**Claude's dev workspace:** `development/src/claude_workspace.clj` — Claude Code's own scratch namespace with system lifecycle helpers (`go!`, `halt!`, `restart!`) and migration helpers (`migrate!`, `rollback!`, `reset-db!`, `seed-db!`). Require it at the REPL with `(require 'claude-workspace)` or add evals directly to the scratch `comment` block. Keep dev helpers here rather than polluting `workspace.clj`.

```clojure
(go!)       ; start system — runs migrations + starts Jetty on :3001
(halt!)     ; stop
(restart!)  ; halt then go
```

**Running a specific test namespace:**

```bash
# From a base or component directory
clojure -A:test -e "(require 'clojure.test '<namespace>) (clojure.test/run-tests '<namespace>)"
```

Or load namespaces in the REPL and call `(clojure.test/run-all-tests)`.

## Architecture

This is a **Polylith monorepo** for an RTS tournament platform. Polylith enforces three kinds of artefacts:

- **components/** — shared units of behaviour, each with a public `interface` namespace
- **bases/** — runnable entry points that wire components together
- **projects/** — deployable combinations (`api`, `deploy-data`) with their own `deps.edn` and `build.clj`

### Component dependency order

```
rts-web → rts-domain → rts-data-access
                    ↘ rts-data (migrations)
rts-web, rts-domain, rts-data-access → http, jdbc, schema, content-negotiation
```

### Request lifecycle (rts-api base)

1. **Reitit** routes the request, coerces path/query/body via Malli schemas
2. **Muuntaja** negotiates content type (`application/json`, `application/hal+json`, `text/html`, `application/htmx+html`)
3. **Integrant**-managed handler (`init-key`) receives injected `{:db … :router …}`
4. Handler calls domain functions. Domain signals errors in two ways:
   - **Logic/validation errors** (budget exceeded, duplicate lord, etc.) — returned as a typed map `{:type :draft/add-error :message "…"}`; web handlers dispatch on `:type` to choose the HTTP status and body. **No try/catch in web handlers.**
   - **Infrastructure/missing-resource errors** — thrown as `ex-info` with `:error/kind` (`:error/missing`, `:error/invalid`, `:error/conflict`). These propagate through the stack and are caught by the Reitit exception middleware in `web.clj` (`exception-handlers`), which maps them to the appropriate HTTP status and renders an error page or JSON body.
5. On success, the **model transformer** walks the resource, resolves `:model/link` annotations into `_links` URLs using the reitit router
6. For HTML responses, a Selmer template is chosen by `:type` in the response body (dispatch map in `web.clj`)

### Comments

Prefer docstrings over `;;` comments for all `defn` and `defn-` forms. Use `;;` comments only for non-function top-level forms (e.g. `def`, `defmulti`) or for inline logic annotations that are genuinely not self-evident.

### Key patterns

**HATEOAS everywhere.** Every resource carries `_links`. Foreign-key fields are annotated with `[:field {:model/link :route/name} :uuid]`; the transformer resolves the named route to a URL automatically.

**Schema separation.** *Entity schemas* (in `rts-data-access`) mirror DB columns. *Resource schemas* (in `rts-domain`) are what the API returns. Handlers map entity → resource (add `:type`, shape links, etc.).

**Integrant DI.** Each handler is an `integrant.core/init-key` method. System wiring lives in `configuration.clj`. Add a new handler by adding an `init-key` method, a `halt-key!` if needed, and a `ref` in the system map.

**SQL in resources.** Queries live in `.sql` files under `resources/<base>/sql/` and are loaded by `next.jdbc`. The `jdbc` component provides thin wrappers (`query-for-entity`, `insert!`, etc.) that apply camel-snake-kebab column mapping and the `sqlite-transformer`.

**IMPORTANT — data-access layer must never call `execute!` or `execute-one!` directly for reading data.** Always use the higher-level `jdbc.contract` wrappers:
- `query-for-entities` / `query-for-entity` — for SELECT queries; applies `sqlite-transformer` (converts string UUIDs → `java.util.UUID`, etc.)
- `insert!` — for INSERT statements
- `execute!` / `execute-one!` — only acceptable for write operations (UPSERT/UPDATE/DELETE) where the result is not read back
- `entity-by-eid` — shorthand for fetching a single row by eid column

Bypassing these wrappers omits the `sqlite-transformer`, which silently returns raw strings instead of typed values and causes Malli coercion failures at the response boundary.

## Adding a new resource (checklist)

1. Entity + resource Malli schemas in `domain/<resource>.clj` — merge from `base-resource`; annotate FK fields with `:model/link`
2. SQL files in `resources/<base>/sql/` + `db/<resource>.clj`
3. Handler functions in `handlers/<resource>.clj`: fetch fns return nil for missing; validation errors return a typed map `{:type :<resource>/error :message "…"}`; infrastructure failures throw and propagate to the Reitit exception middleware
4. Web fetch fns in `web/<resource>.clj` return `{:type :missing/resource :name "<resource>" :id eid}` when domain returns nil — no throwing
5. Route definitions in `web/<resource>.clj` with `:name`, `:parameters`, `:responses`, `:produces`, and an Integrant `ref` for the handler; web handlers dispatch on `:type` of the return value — **no try/catch**
6. Register routes in `web/routes.clj` and handler keys in `configuration.clj`
7. HTML templates under `resources/<base>/` + view dispatch entry in `web.clj` `view-by-type` map (keyed by keyword `:type`)

## Accessibility

Target: **WCAG 2.1 AA**. See `docs/frontend.md` for full patterns with examples.

**Every template must have:**
- A `{% block title %}` override — page title updates on HTMX navigation too (OOB swap for partial responses)
- `aria-labelledby` on every `<section>`, pointing to its heading

**Images:**
- Decorative images (icons, logos set by JS): `alt=""`
- Informative images: descriptive `alt` text

**Dropdowns (Hyperscript-driven):**
- Trigger button: `aria-haspopup="true"` `aria-expanded="false"` `aria-controls="<menu-id>"`
- Update `aria-expanded` in the `_=` Hyperscript alongside the `hidden` toggle
- Menu container: `role="menu"` — items: `role="menuitem"`
- Non-interactive rows (labels, dividers): `role="presentation"` / `role="separator"`

**Forms:**
- Required fields: `required` + `aria-required="true"`
- Error containers: `role="alert" aria-live="assertive"` (present in DOM but `hidden` until needed)
- Form `aria-labelledby` pointing to the page heading

**Tables:**
- Column headers: `scope="col"` on every `<th>`
- Caption: `<caption class="sr-only">` describing the table's contents

**HTMX partial swaps (`hx-target="#content"`):**
- Include `<title id="page-title" hx-swap-oob="true">…</title>` in every resource fragment
- Full-page responses (hx-boost) pick up the `<title>` automatically

**Error fragments:** `role="alert"` on the root element so screen readers announce immediately.



Tests are **unit tests with stubbed database boundaries** — no live DB, no HTTP layer, no Integrant system.

- **Domain schema tests:** call `malli.core/validate` directly on schemas
- **Handler tests:** stub db namespaces with `with-redefs`; pass `{:connection nil}` as deps; assert `:type` assignment, field preservation, nil/empty edge cases
- **What is not tested:** SQL queries, routes/middleware, Selmer templates, content negotiation

Test files mirror source under `bases/<base>/test/` and `components/<component>/test/`.

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `RTS_API_PORT` | `3001` | HTTP listen port |
| `RTS_API_HOSTNAME` | `http://localhost:3001` | Base URL for link generation |
| `AUTH_HOSTNAME` | `http://localhost:4000` | Ory auth service base URL |
| `AUTH_SLUG` | — | Ory tenant slug |

Development SQLite database: `db/database.db` (gitignored). Migrations apply automatically on `(go!)`.
