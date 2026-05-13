(ns com.devereux-henley.rts-web.orchestration
  "Backend registry mapping /components fragment ids to the HX-Trigger
  event names they should listen for. /actions mutations emit one of
  these events on success; the named fragments subscribe to the events
  via `hx-trigger=\"{{ listeners.<fragment-id> }} from:body\"` and refetch
  themselves to reflect the new state.

  Adding a new mutation:
    1. Pick a kebab-case `<resource>-<crud-verb>` past-tense event name.
    2. Decide which fragments display state that the mutation affects.
    3. Add (or extend) entries here so each listening fragment names the
       new event in its space-joined value.
    4. Wire the event into the mutation handler via HX-Trigger.
    5. Make sure each listening fragment's template uses
       `hx-trigger=\"{{ listeners.<fragment-id> }} from:body\"`."
  (:require
   [clojure.string :as string]))

(def ^:private fragment-events
  "fragment-id (keyword) → vector of HX-Trigger event names the fragment
   wants to listen for."
  {:unit-panel       ["draft-entry-created" "draft-entry-updated" "draft-entry-deleted"]
   :draft-stage      ["draft-entry-created" "draft-entry-updated" "draft-entry-deleted" "draft-updated"]
   :tournament-stage ["tournament-status-updated" "tournament-entry-created"
                      "tournament-entry-deleted" "tournament-registration-updated"
                      "tournament-round-created"]})

(def fragment-listeners
  "fragment-id (keyword) → HTMX-ready hx-trigger value.

   Each event is a comma-separated trigger spec with `from:body` so the
   fragment can listen to events fired anywhere in the document body —
   HX-Trigger headers fire events on the body element."
  (into {}
        (map (fn [[k events]]
               [k (->> events
                       (map #(str % " from:body"))
                       (string/join ", "))]))
        fragment-events))

(defn assoc-listeners
  "Adds the registry under `:listeners` on the given Selmer context so
   templates can resolve event names via `{{ listeners.<fragment-id> }}`."
  [context]
  (assoc context :listeners fragment-listeners))
