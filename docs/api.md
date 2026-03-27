# API

## Philosophy

APIs in this repository are designed around **HATEOAS** (Hypermedia as the Engine of Application State). Every resource response carries the links a client needs to discover and navigate related state — the client never constructs URLs manually.

Alongside HATEOAS, every route supports **content negotiation**: the same handler can return `application/json`, `application/hal+json`, `text/html`, or `application/htmx+html` depending on the `Accept` header. This allows API endpoints and server-rendered htmx views to share route definitions and handlers.

`rts-api` is the reference base for these patterns. New API bases should follow its structure.

---

## Polylith components

| Component | Purpose |
|---|---|
| `schema` | Malli schema types, base resource schemas, and the model transformer that generates `_links` |
| `content-negotiation` | Muuntaja format definitions for `text/html` and `application/htmx+html` |

---

## Resource schema

All resources are built on two base schemas defined in `schema/contract.clj`.

### Single resource

Every resource includes an `eid` (UUID), a `type` discriminator, and a `_links` map.

```clojure
base-resource
;; {:eid        uuid
;;  :type       string
;;  :_links     {keyword url, ...}}
```

Domain schemas merge into `base-resource`:

```clojure
(malli.util/merge
  base-resource
  (to-schema
    [:name        :string]
    [:description :string]))
```

### Collection resource

Collections wrap a results array with pagination metadata and navigation links.

```clojure
base-collection-resource
;; {:specification {:collection/size   int
;;                  :collection/offset int
;;                  :collection/since  instant}
;;  :_links        {:self     url
;;                  :first    url
;;                  :last     url
;;                  :next     url      ;; omitted on last page
;;                  :previous url}     ;; omitted on first page
;;  :_embedded     {:results [resource, ...]}}
```

### Field-level link annotation

Fields can declare a HATEOAS link using the `:model/link` property. The model transformer reads this property and populates `_links` automatically.

```clojure
[:game-eid {:model/link :game/by-eid} :uuid]
;; produces: {:_links {:game <url to /api/game/:game-eid>}}
```

The value of `:model/link` must match a named route in `web/routes.clj`. The transformer resolves the URL using the reitit router.

### Custom schema types

The `schema` component registers the following types with Malli:

| Type | Description |
|---|---|
| `:instant` | ISO 8601 datetime, encoded/decoded as `java.time.Instant` |
| `:local-date` | Date without time |
| `:url` | String-based URL |
| `:pos-int` | Positive integer |
| `:neg-int` | Negative integer |

---

## Content negotiation

Content type selection is handled by Muuntaja middleware. The formats registered in each base are:

| Content type | Behaviour |
|---|---|
| `application/json` | Default JSON serialisation |
| `application/hal+json` | Alias for `application/json`; signals HAL intent to clients |
| `text/html` | Renders a Selmer template determined by `response.type` |
| `application/htmx+html` | Same as `text/html` but supports htmx out-of-band swaps |

The middleware stack applies in this order:

1. `format-negotiate-middleware` — selects encoder/decoder from `Accept` / `Content-Type`
2. `format-response-middleware` — encodes the response body
3. `format-request-middleware` — decodes the request body
4. `coerce-response-middleware` — validates the response body against its declared schema
5. `coerce-request-middleware` — validates the request path, query, and body parameters

### HTML and htmx rendering

For `text/html` and `application/htmx+html`, the encoder dispatches to a Selmer template based on the `:type` key in the response body. The view routing map lives in `web.clj`:

```clojure
{"game/game"  "rts-api/resource/game.html"
 "exception"  "rts-api/resource/error.html"
 :default     "rts-api/resource/unknown.html"}
```

The `application/htmx+html` format additionally supports `hx-swap-oob` for out-of-band partial updates.

---

## Route structure

Routes are defined with reitit in `web/routes.clj` as nested vectors. Each route carries a `:name` that is used by the model transformer to generate `_links` URLs.

```clojure
["/api/game/:eid"
 {:name       :game/by-eid
  :parameters {:path  [:map [:eid :uuid]]
               :query [:map
                        [:embed {:optional true} [:set [:enum :factions :socials]]]]}
  :responses  {200 {:body game-resource-schema}}
  :produces   ["application/json"
               "application/hal+json"
               "application/htmx+html"]
  :handler    (integrant.core/ref ::web.game/get-game)}]
```

### Route name conventions

Route names are namespaced keywords that identify the resource and the access pattern:

| Pattern | Example |
|---|---|
| Single resource by eid | `:game/by-eid` |
| Collection | `:tournament/collection` |
| Nested single resource | `:game.faction/by-eid` |
| View (HTML page) | `:view/game` |

---

## Handler pattern

Handlers are registered via Integrant. Each handler is an `init-key` method that receives its dependencies from the system configuration and returns a Ring handler function.

```clojure
(defmethod integrant.core/init-key ::web.game/get-game
  [_key {:keys [db router] :as dependencies}]
  (fn [{{{:keys [eid]} :path
         {:keys [embed]} :query} :parameters
        :as request}]
    ;; ...
    ))
```

### Either monad pipeline

Handlers use `cats.monad.either` to sequence operations. Each step returns `either/right` on success or `either/left` on failure. A left value short-circuits the pipeline.

```clojure
(cats/>>= (either/right eid)
          (partial db/fetch-game dependencies)
          (partial load-embedded dependencies embed))
```

The result is branched into an error or success response:

```clojure
(either/branch
  result
  to-error-response
  (partial to-success-response game-resource-schema route-data))
```

### Standard handler helpers

`handlers/core.clj` provides helpers that codify the common cases:

| Helper | Use |
|---|---|
| `standard-fetch` | Fetch a single resource by eid |
| `standard-fetch-collection` | Fetch a paginated collection |
| `standard-create` | Create a resource and return 201 |
| `standard-load-embedded` | Fetch and attach embedded sub-resources |

---

## Response shapes

### Success — single resource (200)

```clojure
{:status 200
 :body   {:eid    #uuid "..."
          :type   "game/game"
          :name   "StarCraft II"
          :_links {:self #url "http://host/api/game/:eid"}}}
```

### Success — collection (200)

```clojure
{:status 200
 :body   {:specification {:collection/size   20
                          :collection/offset 0
                          :collection/since  #inst "..."}
          :_links        {:self  #url "..."
                          :first #url "..."
                          :next  #url "..."}
          :_embedded     {:results [{...} {...}]}}}
```

### Success — created (201)

Same shape as a single resource response with `:status 201`.

### Embedded resources

Resources can include an `_embedded` map with pre-fetched sub-resources. Embedding is opt-in via a query parameter:

```
GET /api/game/:eid?embed=factions&embed=socials
```

---

## Error responses

All errors follow a consistent shape. The `:error/kind` keyword determines the HTTP status.

| `:error/kind` | Status | Meaning |
|---|---|---|
| `:error/invalid` | 400 | Request failed schema coercion |
| `:error/missing` | 404 | Resource not found |
| `:error/conflict` | 409 | State conflict (e.g. duplicate) |
| `:error/unknown` | 500 | Unexpected server error |

```clojure
;; 404
{:status 404
 :body   {:error/kind    :error/missing
          :model/type    :game/game
          :model/eid     #uuid "..."
          :error/message "No game with given eid."}}

;; 400
{:status 400
 :body   {:error/kind :error/invalid
          :model/type :game/game
          :coercion   {...malli coercion errors...}}}
```

---

## Data access

Database queries are written in SQL and stored as resources under `resources/<base-name>/sql/`. They are loaded and executed via `next.jdbc`.

### Transformers

Two Malli transformers handle type conversion across the data boundary:

| Transformer | Direction | Purpose |
|---|---|---|
| `sqlite-transformer` | DB → Clojure | Parses UUIDs, instants, and other types from SQLite strings |
| `model-transformer` | Clojure → Response | Resolves `:model/link` annotations into `_links` URLs using the reitit router |

### Entity schema convention

Database entities mirror their table columns with Malli schemas. Entity schemas are separate from resource schemas — the handler is responsible for mapping an entity to a resource (adding `:type`, computing links, etc.).

```clojure
game-entity
;; {:id         int
;;  :eid        uuid
;;  :name       string
;;  :description string
;;  :version    int
;;  :created-at instant
;;  :updated-at instant
;;  :deleted-at instant}   ;; soft delete
```

---

## Dependency injection

System components are wired with **Integrant**. The system map is defined in `configuration.clj`. Each key declares its dependencies via `integrant.core/ref`, and each `init-key` method receives a resolved dependency map.

```clojure
{::web.game/get-game {:db     (integrant.core/ref ::db/connection)
                      :router (integrant.core/ref ::web/router)}}
```

### Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `RTS_API_PORT` | `3001` | HTTP listen port |
| `RTS_API_HOSTNAME` | `http://localhost:3001` | Base URL for link generation |
| `AUTH_HOSTNAME` | `http://localhost:4000` | Ory auth service base URL |
| `AUTH_SLUG` | — | Ory tenant slug |

---

## Authentication

Session authentication is handled by `ory-session-middleware`, which wraps the Ring handler stack. On each request it looks for a session cookie, fetches session data from Ory's `/sessions/whoami` endpoint, and attaches it to the request under `:ory-session`. Handlers and templates read identity information from this key.

---

## Adding a new resource

1. **Define entity and resource schemas** in the base's `schema.clj`. Merge from `base-resource` or `base-collection-resource`. Annotate foreign-key fields with `:model/link`.
2. **Write SQL queries** in `resources/<base>/sql/` and expose them through a `db/<resource>.clj` namespace. Return either values.
3. **Implement handler functions** in `handlers/<resource>.clj` using the either pipeline and `standard-*` helpers from `handlers/core.clj`.
4. **Expose endpoints** in `web/<resource>.clj`. Declare `:name`, `:parameters`, `:responses`, and `:produces` on each route.
5. **Register routes** in `web/routes.clj` and **register handler init-keys** in `configuration.clj`.
6. **Add HTML templates** under `resources/<base>/` for `text/html` and `application/htmx+html` support, and register them in the view dispatch map in `web.clj`.
