# Content Negotiation & HATEOAS — rts-api specifics

This document covers rts-api-specific details that build on the conceptual patterns in [docs/api.md](../api.md).

## View-by-type dispatch

For `text/html` and `application/htmx+html`, the encoder dispatches to a Selmer template based on the `:type` key in the response body. The routing map (`view-by-type`) lives in `bases/rts-api/src/.../web.clj`:

```clojure
{:game/game            "rts-web/resource/game.html"
 :game/faction         "rts-web/resource/faction.html"
 :game/draft           "rts-web/resource/draft.html"
 :draft/unit           "rts-web/resource/draft-unit.html"
 :draft/add-success    "rts-web/resource/draft-add-success.html"
 :draft/add-error      "rts-web/resource/draft-add-error.html"
 :draft/remove-success "rts-web/resource/draft-remove-success.html"
 :missing/resource     "rts-web/resource/missing.html"
 "exception"           "rts-web/resource/error.html"}
```

## Error rendering

The Reitit exception middleware in `web.clj` (`exception-handlers`) maps exception types to HTTP statuses and renders responses:

| Exception type | Status | Trigger |
|---|---|---|
| `clojure.lang.ExceptionInfo` with `:error/missing` | 404 | Resource not found (thrown path) |
| `clojure.lang.ExceptionInfo` with `:error/invalid` | 400 | Invalid input |
| `clojure.lang.ExceptionInfo` with `:error/conflict` | 409 | State conflict |
| `clojure.lang.ExceptionInfo` (other) | 500 | Unexpected application error |
| `::coercion/request-coercion` | 400 | Malli request coercion failure |
| `::coercion/response-coercion` | 500 | Malli response coercion failure |
| `java.net.ConnectException` | 503 | Auth service unreachable |
| `::exception/default` | 500 | Any other unhandled exception |

For HTML requests the middleware renders `rts-web/view/error.html` with the session attached; for htmx partial requests it renders an inline error fragment; for JSON requests it returns `{:error <message>}`.

## Route examples

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

## Missing resource rendering

For HTML requests, `:missing/resource` renders `rts-web/resource/missing.html`. For htmx partial requests, the `:missing/resource` type is dispatched through `view-by-type` in `web.clj`.
