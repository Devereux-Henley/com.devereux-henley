# Global HTMX error slot

## Problem

HTMX error fragments produced by `rts-api.web/render-error-fragment` (used
for all 4xx/5xx responses to requests with an `HX-Request` header) are
delivered as a plain `<section class="resource" role="alert">` with no
`hx-swap-oob` attribute and no fixed target. The triggering element's
`hx-swap` governs where the fragment lands.

This works for pages that use an inline swap target (e.g. a main content
region), but breaks for pages whose triggering elements use `hx-swap="none"`
because their success responses are entirely OOB. The draft-unit panel is
the concrete example today:

- `draft-unit.html` Add buttons use `hx-swap="none"` (success swaps via
  OOB section contexts)
- `draft-add-error.html` (422 business errors) is itself a one-line OOB
  fragment targeting `#draft-action-error` — that path works
- **Coercion 400s** and any other generic error path returning
  `render-error-fragment` are silently dropped because the `<section>` has
  no OOB metadata, so the forced `shouldSwap=true` from the entrypoint
  `htmx:beforeSwap` handler finds nothing to place

In short: business errors display, infrastructure/coercion errors don't,
and the mismatch is exactly on the pages that need robust error feedback.

## Constraints

Whatever we build has to work across every current and future HTMX-driven
page, including ones where the trigger has `hx-swap="none"`. Per-page
error slots (like `#draft-action-error`) won't scale — every new HTMX page
would need to remember to include one.

Accessibility also matters: errors need to be announced to screen readers,
and visible errors shouldn't steal focus or shift layout.

## Proposed approach

1. **Layout**: add a fixed toast container to `entrypoint.html`, e.g.

   ```html
   <div id="app-toast"
        class="app-toast"
        role="status"
        aria-live="assertive"
        aria-atomic="true"
        hidden></div>
   ```

   Style it as a fixed-position toast at the top or bottom of the viewport
   so errors don't affect layout flow.

2. **Fragment**: update `render-error-fragment` in
   `bases/rts-api/src/com/devereux_henley/rts_api/web.clj` to emit an OOB
   fragment targeting `#app-toast`, e.g.

   ```clojure
   (defn ^:private render-error-fragment
     [status message]
     {:status  status
      :headers {"Content-Type" "text/html; charset=utf-8"}
      :body    (str "<div id=\"app-toast\" role=\"alert\""
                    " aria-live=\"assertive\" aria-atomic=\"true\""
                    " hx-swap-oob=\"true\">"
                    (escape-html message)
                    "</div>")})
   ```

   The OOB swap replaces the slot, so successive errors overwrite rather
   than stack.

3. **Dismissal**: auto-hide after a timeout via a small Hyperscript snippet
   on the toast container (e.g. `_="on htmx:oobAfterSwap wait 6s then
   add @hidden"`), plus a manual close button for assertive errors.

4. **Draft panel interop**: keep `#draft-action-error` for 422 business
   errors (they stay inline, visually close to the action buttons) and
   let generic infrastructure errors flow into the global toast.
   `draft-add-error.html` and `draft-add-success.html` already target
   `#draft-action-error` explicitly and can stay as-is.

## Open questions

- **Focus management**: should the toast receive focus on error? For
  assertive errors it may be worth it; for retry-able validation errors
  probably not (would interrupt the user mid-edit).
- **Stacking**: if two errors fire in quick succession, should the second
  replace the first or queue? Simplest is OOB overwrite (replace).
- **Success/info toasts**: the same slot could double as a generic toast
  for success messages (e.g. "Draft saved"). Keep one slot or split into
  `#app-error-toast` + `#app-info-toast`?
- **Escaping**: `render-error-fragment` currently string-concats the
  `message` without HTML-escaping. Before routing it to a visible slot,
  add escaping — the current `<p id="error-heading">` rendering is
  similarly vulnerable but less exposed since most messages are static
  literals from the exception handlers.

## Out of scope for this todo

- Full-page error rendering (`render-error-page`) — that path is for
  non-HTMX requests and keeps its current behaviour.
- Typed exception surfacing (`:error/missing` / `:error/invalid` /
  `:error/conflict`) — those already map to sensible status codes; this
  todo is about display, not classification.
