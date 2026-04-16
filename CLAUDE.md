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
clojure -M:build -A api uber           # → target/rts-api.jar
clojure -M:build -A deploy-data uber   # → migrations CLI JAR
clojure -M:build -A rpfm-scraper uber  # → target/rpfm-scraper.jar

# Docker (from bases/rts-api/)
make build_docker
make run_docker
make ory_local            # create Ory Cloud tunnel for local auth

# Refresh game data seed files from RPFM (after a patch)
clojure -M:dev -m com.devereux-henley.rpfm-scraper.core --data-dir bases/rpfm-scraper/data
```

See `docs/rpfm-scraper/game-data.md` for the full RPFM data refresh workflow.

**REPL (primary dev workflow):** Jack-in from the repo root with `M-x cider-jack-in`. The `:dev` alias (configured in `.dir-locals.el`) puts all components and bases on the classpath.

**Claude's dev workspace:** `development/src/claude_workspace.clj` — Claude Code's own scratch namespace with system lifecycle helpers (`go!`, `halt!`, `restart!`) and migration helpers (`migrate!`, `rollback!`, `reset-db!`, `seed-db!`). Keep dev helpers here rather than polluting `workspace.clj`.

**Claude's REPL workflow (preferred over starting the dev server script):**

```bash
# 1. Start nREPL (background — port 7888)
clojure -M:dev:claude -m nrepl.cmdline --port 7888 &

# 2. Connect and boot the system
clj-nrepl-eval -p 7888 "(require 'claude-workspace :reload)"
clj-nrepl-eval -p 7888 "(claude-workspace/go!)"    ; migrations + Jetty on :3001
clj-nrepl-eval -p 7888 "(claude-workspace/seed-db!)" ; seed game data

# 3. After code changes, reload and restart
clj-nrepl-eval -p 7888 "(claude-workspace/restart!)"

# 4. Stop the system
clj-nrepl-eval -p 7888 "(claude-workspace/halt!)"
```

This is faster than `clojure -M:dev -i start_dev_server.clj` because it supports incremental reloading (`restart!`) without a full JVM restart, and gives Claude a REPL for debugging.

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

### Resource schema conventions

Every resource schema follows the same shape so that `handle-fetch-response` / `handle-create-response` can run the model-transformer uniformly over it:

1. **Naming.** Resource schemas end in `-resource` (e.g. `draft-unit-resource`, `draft-entry-resource`, `faction-resource`). Mutation response shapes that aren't full resources use the `-response` suffix.

2. **Merge with `base-resource`.** Every resource is defined via `malli.util/merge schema.contract/base-resource (schema.contract/to-schema [:map ...])`. The base contributes `{:model/type :model/model}` (which the transformer checks), a `:uuid :eid`, a `:type` discriminator slot, and a default `[:_links [:map [:self :url]]]`. The child schema overrides `:type` with a closed `[:= :some/type]` and adds domain fields.

3. **`:model/link` on eid fields.** Annotate the root `:eid` with `{:model/link :<resource>/by-eid}` so the transformer fills `_links.self`. Annotate every foreign-key `*-eid` field with its own `{:model/link :<other>/by-eid}`; the transformer strips `-eid` off the key to derive the `_links` entry name (e.g. `:draft-eid` → `_links.draft`).

4. **Declare `:_links` explicitly when adding links beyond `:self`.** The merge with `base-resource` only gives you `[:_links [:map [:self :url]]]`. If the response carries additional links (any resource with foreign keys does), override `:_links` with the full shape: `[:_links [:map [:self :url] [:draft :url] [:faction :url] ...]]`. Reitit's strip-extra-keys will drop any `_links` entries that aren't declared.

5. **Nested and multi-parameter routes.** For nested resources (e.g. `/draft/:draft-eid/entry/:eid`) name path params after the response schema's fields — `:draft-eid` for the parent's eid and `:eid` for the resource itself. The model-transformer gathers every `*-eid` field from the current map and passes them all to `reitit.core/match-by-name!`, so multi-param routes resolve without special handling. Don't reuse `:eid` for both levels — keep the outer path param explicit (`:draft-eid`) to avoid the collision with the resource's own identity.

6. **Route naming.** Routes that resolve a resource by its eid carry `:name :<domain>/by-eid`. Nested resources follow the same pattern with a domain-qualified key: `:draft-unit/by-eid`, `:draft-entry/by-eid`. The transformer's `_links` key derivation and the `by-eid` naming are how `:model/link` annotations map back to URLs.

7. **Handlers route through `handle-fetch-response`.** GET handlers that want their response encoded with the model-transformer (i.e. any resource with `_links`) call `http.contract/handle-fetch-response resource-schema {:hostname … :router router} thunk` instead of returning `{:status 200 :body raw-map}` directly. Without this the transformer never runs and `_links` won't appear in the response.

8. **UI-only fields don't belong on resources.** Anything a template needs that isn't part of the domain model (section labels like `"Main Army"`, booleans driving animations) stays out of the schema — compute it inline in the Selmer template from the fields that *are* in the schema. Clients using `application/json` or `application/edn` see only the domain model.

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



**Unit tests** are stubbed at the database boundary — no live DB, no HTTP layer, no Integrant system. See `docs/backend-testing.md`.

- **Domain schema tests:** call `malli.core/validate` directly on schemas
- **Handler tests:** stub db namespaces with `with-redefs`; pass `{:connection nil}` as deps; assert `:type` assignment, field preservation, nil/empty edge cases
- Test files live under `components/<component>/test/` mirroring the source layout

**E2E tests** use Playwright against a running dev server. They cover routes, middleware, templates, HTMX interactions, and the HAL+JSON API. See `docs/rts-api/e2e-testing.md`.

- Live in `components/e2e/tests/` as `.spec.js` files
- Run via `clojure -M:poly test` (skip gracefully without server; fail hard in CI)
- Locally: start the dev server first, then run `poly test` or `npx playwright test` from `components/e2e/`

**When implementing a feature that introduces new views or modifies existing templates, the development process is:**

1. **Walk through views with `/playwright-cli`** — after the feature compiles and the dev server is running (via the nREPL workflow), use Playwright interactively to exercise every new page: navigate to it, fill forms, submit, verify the result renders. This catches template bugs, schema coercion issues, and missing route wiring before tests are written. Set the `dev_impersonation` cookie to impersonate users.

2. **Write unit tests in `rts-domain`** — add a test namespace under `components/rts-domain/test/` for the new domain handlers. Stub the data-access layer with `with-redefs`; test `:type` assignment, field mapping, nil/empty edge cases, and any domain logic (validation, state transitions). Follow the patterns in `handlers/draft_test.clj` and `handlers/game_test.clj`.

3. **Write Playwright e2e tests in `components/e2e/tests/`** — add a `.spec.js` file covering the happy path and key edge cases for the new views. Tests should navigate pages, interact with forms, and assert on visible content. Follow the patterns in `navigation.spec.js` and `game-browsing.spec.js`.

```bash
# Interactive Playwright walkthrough (use /playwright-cli skill)
playwright-cli open http://localhost:3001
playwright-cli cookie-set dev_impersonation dev-admin --domain=localhost
playwright-cli goto http://localhost:3001/view/game/<game-eid>/<feature>/index.html
playwright-cli snapshot  # inspect the rendered page

# Run e2e test suite
cd components/e2e && npx playwright test
```

Available dev users (defined in `bases/rts-api/src/.../dev_auth.clj`):
- `dev-admin` — default, auto-applied even without the cookie
- `dev-player-one`
- `dev-player-two`

**PR screenshots** go in the separate [images.com.devereux-henley](https://github.com/Devereux-Henley/images.com.devereux-henley) repo (cloned at `~/Repos/images.com.devereux-henley`), not in this repo. Organize by feature folder (e.g. `tournament-mvp1/`) and reference via raw GitHub URLs in PR descriptions:

```bash
# Take screenshots with playwright-cli
playwright-cli screenshot --filename=/tmp/feature-view.png

# Copy to images repo, commit, push
cp /tmp/feature-view.png ~/Repos/images.com.devereux-henley/<feature>/
cd ~/Repos/images.com.devereux-henley && git add . && git commit -m "Add <feature> screenshots" && git push
```

Reference in PR bodies as: `![alt](https://raw.githubusercontent.com/Devereux-Henley/images.com.devereux-henley/main/<feature>/<file>.png)`

## Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `RTS_API_PORT` | `3001` | HTTP listen port |
| `RTS_API_HOSTNAME` | `http://localhost:3001` | Base URL for link generation |
| `AUTH_HOSTNAME` | `http://localhost:4000` | Ory auth service base URL |
| `AUTH_SLUG` | — | Ory tenant slug |

Development SQLite database: `db/database.db` (gitignored). Migrations apply automatically on `(go!)`.
