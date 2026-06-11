(ns com.devereux-henley.rts-data-access.schema.datalog.draft
  "Datalevin attributes for the `:draft` entity — a player's army-composition
  build for a specific `:game-mode` and `:faction`.

  Drafts are user-mutated, so they carry the audit attributes
  (`:draft/created-by-sub`, `:draft/version`, `:draft/created-at`,
  `:draft/updated-at`) that seed-loaded entities like game/faction/unit
  omit.

  Army entries are `:draft-entry` entities, owned via the cardinality-many
  `:draft/entries` ref. To list a draft's entries:

    (datalog/q '[:find (pull ?e […])
                 :in $ ?draft-eid
                 :where
                 [?d :draft/eid ?draft-eid]
                 [?d :draft/entries ?e]]
               db draft-eid)

  Entries carry `:draft-entry/section` (`:main` vs `:reinforcements`) and
  `:draft-entry/ordinal`, so the `{:main [...], :reinforcements [...]}`
  shape handlers consume is rebuilt by grouping + sorting at the query
  layer.")

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
