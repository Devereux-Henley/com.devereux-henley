(ns com.devereux-henley.rts-data-access.schema.dispute
  "Malli return-shape and write-spec schemas for the dispute-domain Datalevin
  queries. Enum-typed fields carry the string form the reads flatten keywords
  into."
  (:require
   [com.devereux-henley.rts-data-access.schema :as schema]
   [com.devereux-henley.schema.contract :as schema.contract]))

(def dispute-result-schema
  (schema.contract/to-schema
   [:map
    [:eid            :uuid]
    [:tournament-eid [:maybe :uuid]]
    [:match-eid      [:maybe :uuid]]
    [:match-game-eid [:maybe :uuid]]
    [:kind           schema/dispute-kind-enum]
    [:priority       schema/dispute-priority-enum]
    [:status         schema/dispute-status-enum]
    [:reporter-sub   :string]
    [:detail         [:maybe :string]]
    [:opened-at      [:maybe inst?]]
    [:resolved-at    [:maybe inst?]]]))

(def open-spec-schema
  "Caller-supplied fields for opening a dispute. `:match-game-eid` is optional
  (series-level disputes omit it); `:priority` defaults to `normal` when
  omitted."
  (schema.contract/to-schema
   [:map
    [:tournament-eid :uuid]
    [:match-eid      :uuid]
    [:match-game-eid {:optional true} [:maybe :uuid]]
    [:kind           schema/dispute-kind-enum]
    [:priority       {:optional true} schema/dispute-priority-enum]
    [:reporter-sub   :string]
    [:detail         {:optional true} [:maybe :string]]]))
