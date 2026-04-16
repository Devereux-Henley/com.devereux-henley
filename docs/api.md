# API

## Philosophy

APIs in this repository are designed around **HATEOAS** (Hypermedia as the Engine of Application State). Every resource response carries the links a client needs to discover and navigate related state — the client never constructs URLs manually.

Alongside HATEOAS, every route supports **content negotiation**: the same handler can return `application/json`, `application/hal+json`, `text/html`, or `application/htmx+html` depending on the `Accept` header. This allows API endpoints and server-rendered htmx views to share route definitions and handlers.

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

The value of `:model/link` must match a named route. The transformer resolves the URL using the reitit router.

### Custom schema types

The `schema` component registers the following types with Malli:

| Type | Description |
|---|---|
| `:instant` | ISO 8601 datetime, encoded/decoded as `java.time.Instant` |
| `:local-date` | Date without time, `java.time.LocalDate` |
| `:local-datetime` | Date-time without timezone, `java.time.LocalDateTime` |
| `:timezone-id` | IANA timezone identifier, `java.time.ZoneId` |
| `:url` | String-based URL |
| `:pos-int` | Positive integer |
| `:neg-int` | Negative integer |

---

## Content negotiation

Content type selection is handled by Muuntaja middleware. The formats available to each base are:

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

---

## Route structure

Routes are defined with reitit as nested vectors. Each route carries a `:name` that is used by the model transformer to generate `_links` URLs.

### Route name conventions

Route names are namespaced keywords that identify the resource and the access pattern:

| Pattern | Example |
|---|---|
| Single resource by eid | `:game/by-eid` |
| Collection | `:tournament/collection` |
| Nested single resource | `:game.faction/by-eid` |

---

## Handler pattern

Handlers are registered via Integrant. Each handler is an `init-key` method that receives its dependencies from the system configuration and returns a Ring handler function.

### Error signalling conventions

Domain and web functions signal errors in two distinct ways:

**Return a typed map** for logic and validation errors — conditions the caller is expected to handle as normal outcomes (e.g. budget exceeded, duplicate entry, missing resource):

```clojure
;; domain validation error
{:type :draft/add-error :message "Only one lord may be added to an army section."}

;; missing resource (returned by web-layer fetch fns when domain returns nil)
{:type :missing/resource :name "game" :id #uuid "..."}
```

Web handlers dispatch on `:type` — no try/catch:

```clojure
(let [result (domain/add-unit dependencies eid unit-eid section)]
  {:status (if (= :draft/add-success (:type result)) 200 422)
   :body   result})
```

**Throw `ex-info`** only for infrastructure failures — conditions callers cannot handle meaningfully. These propagate to the Reitit exception middleware, which maps `:error/kind` to an HTTP status.

### Fetch functions

Web-layer fetch functions call domain functions and return a typed missing-resource map when the domain returns nil:

```clojure
(defn get-resource-by-eid
  [dependencies eid]
  (or (domain/get-resource-by-eid dependencies eid)
      {:type :missing/resource :name "resource" :id eid}))
```

### Response helpers (`http/contract.clj`)

| Helper | Behaviour |
|---|---|
| `handle-fetch-response` | Calls thunk; returns 404 if result is `:missing/resource`, otherwise encodes and returns 200 |
| `handle-create-response` | Calls thunk; encodes and returns 201 |
| `apply-embeds` | Threads embed fns through a model; short-circuits on `:missing/resource` |
| `encode-value` | Applies the model transformer to resolve `_links` |

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
GET /api/resource/:eid?embed=children&embed=relations
```

---

## Error responses

### Missing resource (404)

```clojure
{:status 404
 :body   {:type :missing/resource
          :name "resource"
          :id   #uuid "..."}}
```

### Validation / logic error

Domain functions return a typed error map for expected failure conditions. The web handler maps this to an appropriate HTTP status:

```clojure
{:status 422
 :body   {:type    :domain/error
          :message "Validation failed."}}
```

### Infrastructure / coercion error

Unhandled exceptions propagate to the Reitit exception middleware. See per-base documentation for the specific error-to-status mappings.

---

## Data access

Database queries are written in SQL and stored as resources under `resources/<component>/sql/`. They are loaded and executed via `next.jdbc`.

### Transformers

Two Malli transformers handle type conversion across the data boundary:

| Transformer | Direction | Purpose |
|---|---|---|
| `sqlite-transformer` | DB → Clojure | Parses UUIDs, instants, and other types from SQLite strings |
| `model-transformer` | Clojure → Response | Resolves `:model/link` annotations into `_links` URLs using the reitit router |

### Entity schema convention

Database entities mirror their table columns with Malli schemas. Entity schemas are separate from resource schemas — the handler is responsible for mapping an entity to a resource (adding `:type`, computing links, etc.).

---

## Dependency injection

System components are wired with **Integrant**. The system map is defined in each base's `configuration.clj`. Each key declares its dependencies via `integrant.core/ref`, and each `init-key` method receives a resolved dependency map.

---

## Adding a new resource

1. **Define entity and resource schemas.** Merge from `base-resource` or `base-collection-resource`. Annotate foreign-key fields with `:model/link`.
2. **Write SQL queries** and expose them through a data-access namespace.
3. **Implement domain functions:** fetch functions return the entity or `nil`; validation failures return a typed error map; infrastructure failures throw.
4. **Expose endpoints** in a web handler namespace. Declare `:name`, `:parameters`, `:responses`, and `:produces` on each route. Web handlers dispatch on `:type` — **no try/catch**.
5. **Register routes** and **register handler init-keys** in configuration.
6. **Add HTML templates** for `text/html` and `application/htmx+html` support.

See per-base documentation for specific file paths and conventions.
