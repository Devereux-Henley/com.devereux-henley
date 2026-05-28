(ns com.devereux-henley.rts-data-access.schema.datalog.unit
  "Datalevin attributes for the `:unit` entity.

  `:unit/mark` is a `:db.type/keyword` enum
  `#{:khorne :nurgle :slaanesh :tzeentch :undivided}` rather than a string
  with a CHECK constraint — marks have no rows of their own, so the SQLite
  enum becomes a domain-side invariant.

  `:unit/lore` stays a plain engine-key string (not a ref to `:lore`) because
  the SQLite era stored only the key and the views resolve lores by key
  separately.

  Per-patch statlines hang off `:unit` via the reverse of
  `:unit-statistics/unit` — pull `{:unit-statistics/_unit [...]}` to get
  every snapshot for a unit; query `[?s :unit-statistics/unit ?u]` for
  filter / join shapes.")

(def schema
  {:unit/eid           {:db/valueType :db.type/uuid
                        :db/unique    :db.unique/identity}
   :unit/key           {:db/valueType :db.type/string}
   :unit/name          {:db/valueType :db.type/string}
   :unit/family-name   {:db/valueType :db.type/string}
   :unit/description   {:db/valueType :db.type/string}
   :unit/game          {:db/valueType :db.type/ref}
   :unit/faction       {:db/valueType :db.type/ref}
   :unit/unit-type     {:db/valueType :db.type/ref}
   :unit/unit-category {:db/valueType :db.type/ref}
   :unit/mark          {:db/valueType :db.type/keyword}
   :unit/lore          {:db/valueType :db.type/string}
   :unit/is-unique     {:db/valueType :db.type/boolean}})
