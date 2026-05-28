(ns com.devereux-henley.rts-data-access.schema.datalog.draft-entry
  "Datalevin attributes for the `:draft-entry` entity — one army slot in a
  draft. Decomposed out of the SQLite `draft_state.state` JSON blob, where
  each entry was a map under `:main` / `:reinforcements`.

  `:draft-entry/section` is a keyword enum `#{:main :reinforcements}`
  (mirrors `:unit/mark`'s SQLite-CHECK-becomes-domain-invariant pattern).
  `:draft-entry/ordinal` preserves the JSON-array order within a section
  so rendering stays stable across edits — sets in datalog are unordered,
  so a per-section `(:draft-entry/ordinal)` sort restores the prior shape.

  `:draft-entry/abilities`, `…/spells`, `…/items` stay as cardinality-many
  string keys (engine keys), matching how `:unit-mount/granted-ability-keys`
  and `:unit/lore` handle key references — joins against `:ability/key`,
  `:spell/key`, `:item/key` happen at the query layer when full resources
  are needed.

  `:draft-entry/total-cost` is the app-computed total for the unit as
  configured (mount + level + items). `:draft-entry/engine-cost` is the
  parser-emitted cost from the replay-parsed flow; a divergence between
  the two is a data signal, not an error.

  Entries don't carry a back-pointer to their owning draft — the
  relationship lives on the parent as the cardinality-many
  `:draft/entries`. To find a parent draft from an entry-eid, query
  `[?d :draft/entries ?e]` or pull `:draft/_entries`.")

(def schema
  {:draft-entry/eid         {:db/valueType :db.type/uuid
                             :db/unique    :db.unique/identity}
   :draft-entry/unit        {:db/valueType :db.type/ref}
   :draft-entry/section     {:db/valueType :db.type/keyword}
   :draft-entry/ordinal     {:db/valueType :db.type/long}
   :draft-entry/mount       {:db/valueType :db.type/string}
   :draft-entry/lore        {:db/valueType :db.type/string}
   :draft-entry/level       {:db/valueType :db.type/long}
   :draft-entry/abilities   {:db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/many}
   :draft-entry/spells      {:db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/many}
   :draft-entry/items       {:db/valueType   :db.type/string
                             :db/cardinality :db.cardinality/many}
   :draft-entry/total-cost  {:db/valueType :db.type/long}
   :draft-entry/engine-cost {:db/valueType :db.type/long}})
