(ns com.devereux-henley.rts-data-access.schema.datalog.game
  "Datalevin schema for the game domain: seed data refreshed from RPFM
  (factions, units, marks, lores, spells, mounts, items, etc.).

  References (`:db.type/ref`) replace the SQLite integer FKs. Junction tables
  remain as their own entities (`:unit-item`, `:spell-lore`, `:unit-mount`)
  rather than collapsing into cardinality-many refs — this preserves the
  existing query surface and leaves room for per-join attributes to land
  without reshaping the pull patterns. Audit columns (`created-by-sub`,
  `version`, `deleted-at`) from the SQLite era are intentionally dropped:
  game data is regenerated from RPFM rather than mutated, so optimistic
  locking and soft delete carry no weight.")

(def schema
  "Game-domain Datalevin attributes. Merged into the workspace-wide
  [[com.devereux-henley.rts-data-access.schema.datalog/schema]]."
  {;; ─── Game ────────────────────────────────────────────────────────────────
   :game/eid                           {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :game/name                          {:db/valueType :db.type/string}
   :game/description                   {:db/valueType :db.type/string}

   ;; ─── Social media platform / link ────────────────────────────────────────
   :social-media-platform/eid          {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :social-media-platform/name         {:db/valueType :db.type/string}
   :social-media-platform/description  {:db/valueType :db.type/string}
   :social-media-platform/platform-url {:db/valueType :db.type/string}

   :game-social-link/eid               {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :game-social-link/url               {:db/valueType :db.type/string}
   :game-social-link/game              {:db/valueType :db.type/ref}
   :game-social-link/platform          {:db/valueType :db.type/ref}

   ;; ─── Unit-type / unit-category ───────────────────────────────────────────
   :unit-type/eid                      {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :unit-type/name                     {:db/valueType :db.type/string}
   :unit-type/description              {:db/valueType :db.type/string}
   :unit-type/game                     {:db/valueType :db.type/ref}

   :unit-category/eid                  {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :unit-category/name                 {:db/valueType :db.type/string}
   :unit-category/description          {:db/valueType :db.type/string}
   :unit-category/game                 {:db/valueType :db.type/ref}

   ;; ─── Faction / subfaction ────────────────────────────────────────────────
   :faction/eid                        {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :faction/name                       {:db/valueType :db.type/string}
   :faction/key                        {:db/valueType :db.type/string}
   :faction/description                {:db/valueType :db.type/string}
   :faction/game                       {:db/valueType :db.type/ref}

   :subfaction/eid                     {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :subfaction/key                     {:db/valueType :db.type/string
                                        :db/unique    :db.unique/identity}
   :subfaction/name                    {:db/valueType :db.type/string}
   :subfaction/faction                 {:db/valueType :db.type/ref}

   ;; ─── Mark / lore / spell / ability / attribute ──────────────────────────
   ;; `:unit/mark` stays a keyword enum (#{:khorne :nurgle :slaanesh :tzeentch
   ;; :undivided}) since marks have no rows of their own. Lores, spells, and
   ;; abilities each get their own entity so units can ref them.

   :lore/eid                           {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :lore/key                           {:db/valueType :db.type/string}
   :lore/name                          {:db/valueType :db.type/string}
   :lore/description                   {:db/valueType :db.type/string}
   :lore/game                          {:db/valueType :db.type/ref}

   :spell/eid                          {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :spell/key                          {:db/valueType :db.type/string}
   :spell/name                         {:db/valueType :db.type/string}
   :spell/description                  {:db/valueType :db.type/string}
   :spell/spell-type                   {:db/valueType :db.type/string}
   :spell/mana-cost                    {:db/valueType :db.type/long}
   :spell/cost                         {:db/valueType :db.type/long}
   :spell/game                         {:db/valueType :db.type/ref}

   :spell-lore/eid                     {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :spell-lore/spell                   {:db/valueType :db.type/ref}
   :spell-lore/lore                    {:db/valueType :db.type/ref}
   :spell-lore/game                    {:db/valueType :db.type/ref}

   :ability/eid                        {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :ability/key                        {:db/valueType :db.type/string}
   :ability/name                       {:db/valueType :db.type/string}
   :ability/description                {:db/valueType :db.type/string}
   :ability/ability-type               {:db/valueType :db.type/string}
   :ability/cost                       {:db/valueType :db.type/long}
   :ability/game                       {:db/valueType :db.type/ref}

   :attribute/eid                      {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :attribute/key                      {:db/valueType :db.type/string}
   :attribute/name                     {:db/valueType :db.type/string}
   :attribute/description              {:db/valueType :db.type/string}
   :attribute/game                     {:db/valueType :db.type/ref}

   ;; ─── Item / unit-item ────────────────────────────────────────────────────
   :item/eid                           {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :item/key                           {:db/valueType :db.type/string}
   :item/name                          {:db/valueType :db.type/string}
   :item/category                      {:db/valueType :db.type/string}
   :item/cost                          {:db/valueType :db.type/long}
   :item/icon-key                      {:db/valueType :db.type/string}
   :item/game                          {:db/valueType :db.type/ref}

   ;; unit_item had no extra columns in SQLite; modelled here as an entity
   ;; (rather than a cardinality-many ref) so a future "starting equipment"
   ;; flag or quantity column can land without changing the surrounding
   ;; pull patterns.
   :unit-item/eid                      {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :unit-item/unit                     {:db/valueType :db.type/ref}
   :unit-item/item                     {:db/valueType :db.type/ref}

   ;; ─── Mount / unit-mount ──────────────────────────────────────────────────
   :mount/eid                          {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :mount/key                          {:db/valueType :db.type/string}
   :mount/name                         {:db/valueType :db.type/string}
   :mount/icon-key                     {:db/valueType :db.type/string}
   :mount/game                         {:db/valueType :db.type/ref}

   ;; unit_mount carries per-mount overrides (cost, stats, ability keys) so
   ;; it stays an entity.
   :unit-mount/eid                     {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :unit-mount/unit                    {:db/valueType :db.type/ref}
   :unit-mount/mount                   {:db/valueType :db.type/ref}
   :unit-mount/cost                    {:db/valueType :db.type/long}
   :unit-mount/stats-override          {:db/valueType :db.type/string}
   :unit-mount/granted-ability-keys    {:db/valueType   :db.type/string
                                        :db/cardinality :db.cardinality/many}

   ;; ─── Unit ────────────────────────────────────────────────────────────────
   :unit/eid                           {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :unit/key                           {:db/valueType :db.type/string}
   :unit/name                          {:db/valueType :db.type/string}
   :unit/family-name                   {:db/valueType :db.type/string}
   :unit/description                   {:db/valueType :db.type/string}
   :unit/game                          {:db/valueType :db.type/ref}
   :unit/faction                       {:db/valueType :db.type/ref}
   :unit/unit-type                     {:db/valueType :db.type/ref}
   :unit/unit-category                 {:db/valueType :db.type/ref}
   ;; Serialized JSON blob — same shape the SQLite era's `unit-statistics`
   ;; column held. The malli `unit-statistics-raw-schema` decodes it; views
   ;; render the parsed map without ever querying its inner fields, so a
   ;; string is the cheapest representation and avoids a schema explosion.
   :unit/unit-statistics               {:db/valueType :db.type/string}
   :unit/mark                          {:db/valueType :db.type/keyword}
   ;; Engine lore key for spellcasters; nil for non-casters. Left as a
   ;; string (not a ref) because the SQL representation stored only the key
   ;; and the views resolve lores by key separately.
   :unit/lore                          {:db/valueType :db.type/string}
   :unit/is-unique                     {:db/valueType :db.type/boolean}

   ;; ─── Game mode ───────────────────────────────────────────────────────────
   :game-mode/eid                      {:db/valueType :db.type/uuid
                                        :db/unique    :db.unique/identity}
   :game-mode/name                     {:db/valueType :db.type/string}
   :game-mode/description              {:db/valueType :db.type/string}
   :game-mode/draft-value              {:db/valueType :db.type/long}
   :game-mode/player-count             {:db/valueType :db.type/long}
   :game-mode/reinforcement-value      {:db/valueType :db.type/long}
   :game-mode/reinforcements-enabled   {:db/valueType :db.type/boolean}
   :game-mode/game                     {:db/valueType :db.type/ref}

   ;; ─── Unit level cost lookup ──────────────────────────────────────────────
   ;; Pure lookup table. Keyed by `level`, which is both the identity and
   ;; the only "name" it has — no public uuid is needed because nothing
   ;; routes to this resource by id.
   :unit-level-cost/level              {:db/valueType :db.type/long
                                        :db/unique    :db.unique/identity}
   :unit-level-cost/fixed-cost         {:db/valueType :db.type/long}
   :unit-level-cost/cost-multiplier    {:db/valueType :db.type/double}
   :unit-level-cost/fatigue            {:db/valueType :db.type/long}
   :unit-level-cost/melee-cp           {:db/valueType :db.type/double}
   :unit-level-cost/missile-cp         {:db/valueType :db.type/double}})
