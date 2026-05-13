(ns com.devereux-henley.rts-web.orchestration
  "Assembles a backend registry that maps event ids to the HX-Trigger
  event names they should listen for. Each namespace that owns mutating
  endpoints contributes its own slice of the registry by deriving an
  integrant key from `::web-trigger-source`; this namespace gathers
  them via `integrant.core/refset` and exposes the merged result.

  Wiring overview:

    contributing ns    integrant
    ──────────────     ─────────
    ::web-triggers ─┐ derive
    {:foo [...]}    │ ::web-trigger-source
                    │ refset
    contributing ns │
    ──────────────  │
    ::web-triggers ─┴──► ::registry ──► ::middleware ──► request
    {:bar [...]}                                          :web-triggers

  Templates read the result via the `:triggers` key in the Selmer
  context (set by `web.view/base-context`) as
  `hx-trigger=\"{{ triggers.<event-id> }}\"`. The `from:body` clause
  is baked into the assembled string.

  Adding a new mutation:
    1. Pick a kebab-case `<resource>-<crud-verb>` past-tense event name.
    2. Decide which fragments display state that the mutation affects.
    3. In the owning namespace, declare (or extend) a
       `::web-triggers` init-key returning `{event-id [event…]}`
       and derive it from `::web-trigger-source`.
    4. Wire the event into the mutation handler via HX-Trigger.
    5. Each listening fragment's template uses
       `hx-trigger=\"{{ triggers.<event-id> }}\"`."
  (:require
   [clojure.string :as string]
   [integrant.core]))

(defn- compile-triggers
  "Turn `{event-id [event…]}` into `{event-id \"event from:body, …\"}` —
   the comma-joined trigger spec HTMX templates can drop straight into
   `hx-trigger`. `from:body` is appended to every event because HX-Trigger
   headers fire events on the document body."
  [web-events]
  (into {}
        (map (fn [[event-id events]]
               [event-id (->> events
                              (map #(str % " from:body"))
                              (string/join ", "))]))
        web-events))

(defmethod integrant.core/init-key ::registry
  [_init-key {:keys [sources]}]
  (compile-triggers (reduce merge {} sources)))

(defmethod integrant.core/init-key ::middleware
  [_init-key {:keys [registry]}]
  (fn [handler]
    (fn [request]
      (handler (assoc request :web-triggers registry)))))
