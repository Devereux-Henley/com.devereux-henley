(ns com.devereux-henley.rts-data-access.schema.datalog.draft
  "Datalevin attributes for the `:draft` entity — a player's army-composition
  build for a specific `:game-mode` and `:faction`.

  Drafts are user-mutated, so the SQLite-era audit columns
  (`:draft/created-by-sub`, `:draft/version`, `:draft/created-at`,
  `:draft/updated-at`) survive the migration; the seed-loader pattern that
  lets game/faction/unit drop those doesn't apply here.

  Army entries are decomposed out of the SQLite `draft_state` JSON blob
  into `:draft-entry` entities, owned via the cardinality-many
  `:draft/entries` ref. To list a draft's entries:

    (datalog/q '[:find (pull ?e […])
                 :in $ ?draft-eid
                 :where
                 [?d :draft/eid ?draft-eid]
                 [?d :draft/entries ?e]]
               db draft-eid)

  Entries carry `:draft-entry/section` (`:main` vs `:reinforcements`) and
  `:draft-entry/ordinal`, so the JSON-era `{:main [...], :reinforcements
  [...]}` shape is rebuilt by grouping + sorting at the query layer.")

(def schema
  {:draft/eid            {:db/valueType :db.type/uuid
                          :db/unique    :db.unique/identity}
   :draft/name           {:db/valueType :db.type/string}
   :draft/player-sub     {:db/valueType :db.type/string}
   :draft/game-mode      {:db/valueType :db.type/ref}
   :draft/faction        {:db/valueType :db.type/ref}
   :draft/version        {:db/valueType :db.type/long}
   :draft/created-by-sub {:db/valueType :db.type/string}
   :draft/created-at     {:db/valueType :db.type/instant}
   :draft/updated-at     {:db/valueType :db.type/instant}
   :draft/entries        {:db/valueType   :db.type/ref
                          :db/cardinality :db.cardinality/many}})
