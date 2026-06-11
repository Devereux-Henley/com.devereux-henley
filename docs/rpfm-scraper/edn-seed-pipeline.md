# RPFM → Datalog EDN seed pipeline

The `rpfm-scraper` base produces the per-patch Datalog EDN seed directly: it
reads **curated authoring EDN** plus RPFM-decoded JSON, emits the generated
portions, and writes the merged seed under
`components/rts-data/resources/rts-data/seed/datalog/<version>/`, where the
API loads it with no build step.

```
seed/authoring/<v>/*.edn ─┐
                          ├─► rpfm-scraper ──► seed/datalog/<v>/*.edn   (merged, committed, loaded by API)
RPFM JSON ────────────────┘     (read curated + RPFM,
                                 emit generated, merge)
```

There are no integer `id` columns: Datalog links by `eid` lookup-refs
(`[:unit/eid #uuid "…"]`), so unit-item / unit-mount junctions link by unit
eid.

## Directory layout

```
components/rts-data/resources/rts-data/seed/
  authoring/<version>/      ← hand-maintained, source of truth for HUMAN decisions
    games.edn  factions.edn  unit-categories.edn  unit-types.edn  lores.edn
    spell-lores.edn  attributes.edn  game-modes.edn
    social-media-platforms.edn  game-social-links.edn
    units.edn        (identity skeleton only — see ownership table)
    abilities.edn    (identity skeleton only)
    spells.edn       (identity + curated fields only)
    overrides.edn    (lore pins, display-name→key, card overrides)
  datalog/<version>/        ← BUILD OUTPUT: authoring ⊕ generated, committed, loaded by the API
    (full set in `datalog-seed/seed-files` order)
```

`authoring/` is what a human edits to add a unit, fix a description, pin a
lore. `datalog/` is reproducible from `authoring/` + RPFM JSON; it is
committed so the API loads it with no build step, and so seed diffs are
reviewable.

## Per-entity ownership

`O` = ownership. **C**urated (authoring, hand-maintained) · **G**enerated
(scraper, from RPFM) · **H**ybrid (identity + curated fields in authoring; rest
generated) · **B**uild metadata.

| seed file | O | Authoring (curated) fields | Generated (RPFM) fields | eid rule |
|---|---|---|---|---|
| patches | B | — | version, released-at | `derived-uuid("patch", version)` |
| games | C | eid, name, description | — | authoring |
| social-media-platforms | C | all | — | authoring |
| game-social-links | C | eid, url, game, platform | — | authoring |
| unit-types | C | eid, name, description, game | — | authoring |
| unit-categories | C | eid, name, description, ordinal, game | — | authoring |
| factions | C | eid, name, key, description, game | — | authoring |
| game-modes | C | all | — | authoring |
| lores | C | eid, key, name, description, game | — | authoring |
| attributes | C | eid, key, name, description, game | — | authoring |
| spell-lores | C | eid, spell, lore, game | — | authoring |
| spells | H | eid, key, name, description, spell-type, mana-cost, game | **cost** | authoring |
| abilities | H | eid, key, ability-type, description, game | **name, cost** | authoring |
| units | H | eid, name, faction, unit-type, unit-category, description, is-unique, game | **key, mark, lore, family-name** | authoring |
| unit-level-cost | C | all (level + cost columns) | — | authoring |
| item-abilities | G | — | key, name, description, ability-type, game (`:ability` rows for the replay ability keys items grant) | `derived-uuid("item-ability", ability-key)` |
| items | G | — | key, name, category, cost, icon-key, abilities (refs into item-abilities), game | `format e1000000-…-%012x` (sorted index) |
| mounts | G | — | key, name, icon-key, game | `format d2000000-…-%012x` (sorted index) |
| subfactions | G | — | key, name, faction (FK from authoring) | UUID v5 (frozen ns + engine key) |
| unit-items | G | — | unit, item links | `derived-uuid("unit-item", unit-eid, item-eid)` |
| unit-mounts | G | — | unit, mount, cost, stats-override, granted-ability-keys | `derived-uuid("unit-mount", unit-eid, mount-eid)` |
| unit-statistics | H | unit, **abilities, draftable-spells** | the RPFM statline (`schema.us/fields` minus the two curated lists) | `derived-uuid("unit-statistics", patch-eid, unit-eid)` |

`derived-uuid` = `UUID/nameUUIDFromBytes` over `"/"`-joined parts, so
generated link/statistics eids are reproducible from the curated identities
with no stored state.

### Hybrid notes

- **spells / abilities**: the row identity is curated; the scraper overwrites
  only the RPFM-derived fields — spell `cost`, ability `name` + `cost`.
  Ability `description` is curated (RPFM's tooltip loc keys don't match
  ability keys), as is the full spell display. `is-unique` on units is
  curated.
- **units**: the curated skeleton is the per-faction unit list a human
  maintains. `key` (engine `land_units` key), `mark`, `lore`, and
  `family-name` are derived by the scraper (name-index match, `unit_set`
  junctions, name-suffix parsing) with curated overrides in `overrides.edn`.
- **unit-statistics** is hybrid: the numeric/string statline is RPFM-derived,
  but `abilities` and `draftable-spells` are **curated** — RPFM has no notion
  of which spells/abilities a unit may *draft*. They live in
  `authoring/<v>/unit-statistics.edn` keyed by `:unit-statistics/unit` (only
  units that have them); the scraper merges them onto the freshly computed
  statline.

  The scraper decomposes its engine-shaped statline into the
  `:unit-statistics/*` attributes using the `schema.us/fields` spec, which is
  owned by `rts-data-access`. To avoid the lean scraper depending on that
  component (and Datalevin), the `[doc-key attr kind]` vector is **replicated**
  in the scraper with a guard test asserting it equals `schema.us/fields`.

## eid stability

- Curated + hybrid-identity eids live in `authoring/` and are stable by
  construction.
- Generated eids are **deterministic functions** of curated identities:
  `derived-uuid`/UUID-v5 for links, subfactions, and statistics.
- **Known risk:** `items` and `mounts` eids are `sorted-index`-based, so
  adding/removing an item or mount **shifts** every later eid. This affects
  any FK that points at item/mount eids across patches. A follow-up could
  switch these to UUID-v5 on the engine key, but that would rewrite existing
  eids.

## Merge

`rts-data` provides:

1. A **read API** for the scraper: read curated authoring EDN
   (`read-authoring <version> <entity>`), exposing the identity/lookup maps
   the scraper needs (name→eid, key→eid, slug→faction-eid,
   lore-suffix→key).
2. A **merge** producing `datalog/<version>/` from curated authoring EDN ⊕
   scraper-generated EDN, in `datalog-seed/seed-files` transact order:
   - fully-curated files: copied whole;
   - hybrid files: authoring identity left-merged with generated fields by eid;
   - generated-only files: written as produced.

The loader (`rts-data.datalog-seed`) reads `datalog/<version>/` unchanged
regardless of how the seed was produced.
