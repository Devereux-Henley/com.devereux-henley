(ns com.devereux-henley.rts-data-access.schema.datalog.dispute
  "Datalevin attributes for the `:dispute` entity — a contested match result
  raised by a player against a `:match` (and optionally a specific
  `:match-game`) within a `:tournament`.

  `:dispute/kind` classifies the contest (`#{:conflicting-result
  :missing-replay :connection-loss :other}`); `:dispute/priority`
  (`#{:urgent :normal :low}`) drives organizer-queue ordering;
  `:dispute/status` (`#{:open :resolved :dismissed}`) tracks the lifecycle.
  `:dispute/opened-at` stamps creation and `:dispute/resolved-at` stamps the
  transition out of `:open` (set on both resolve and dismiss).")

(def schema
  {:dispute/eid          {:db/valueType :db.type/uuid
                          :db/unique    :db.unique/identity}
   :dispute/tournament   {:db/valueType :db.type/ref}
   :dispute/match        {:db/valueType :db.type/ref}
   :dispute/match-game   {:db/valueType :db.type/ref}
   :dispute/kind         {:db/valueType :db.type/keyword}
   :dispute/priority     {:db/valueType :db.type/keyword}
   :dispute/status       {:db/valueType :db.type/keyword}
   :dispute/reporter-sub {:db/valueType :db.type/string}
   :dispute/detail       {:db/valueType :db.type/string}
   :dispute/opened-at    {:db/valueType :db.type/instant}
   :dispute/resolved-at  {:db/valueType :db.type/instant}})
