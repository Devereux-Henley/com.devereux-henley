# RPFM → Datalog EDN seed pipeline

Status: **implemented** (epic `rts-2lg`, closed). The target pipeline below is what
runs today; the "current pipeline (two hops)" section and the SQLite caveats are the
historical design context. Runtime SQLite has since been fully decommissioned (epic
`rts-sld`), so the gated `seed-*.sql` deletion described under "out of scope" has
also landed — no SQL seed or migrations remain in the repo.

## Goal

Make the `rpfm-scraper` base produce the Datalog EDN seed **directly**, eliminating
the SQL round-trip and the `development/src/dump_datalog_seed.clj` tool.

### Scope

In scope (epic `rts-2lg`):

- The scraper reads **curated authoring EDN** + RPFM-decoded JSON and writes the
  **generated** portions of the seed, merged into the runtime Datalog seed under
  `components/rts-data/resources/rts-data/seed/datalog/<version>/`.
- Retire `dump_datalog_seed.clj` (the SQLite-bootstrapping dump tool).

Out of scope (tracked separately under `rts-7xz` → `rts-sld`):

- Decommissioning SQLite **at runtime**. `rts-domain` `stats.clj`
  (`get-faction-standings-for-*`) and the draft-lock check in `draft.clj`
  (`get-draft-lock-info`) still read SQLite, and stats joins game-data tables.
  **Therefore the `seed-*.sql` files and the game-data migrations cannot be
  deleted yet** — they still seed the runtime SQLite DB. This epic stops the
  scraper from *producing* SQL and removes the dump tool; physical deletion of
  the SQL seed + game-data DDL lands when runtime stops reading SQLite game data.

## Current pipeline (two hops)

```
RPFM JSON ─┐
           ├─► rpfm-scraper ──► seed-*.sql ──► dump_datalog_seed ──► seed/datalog/<v>/*.edn
seed-*.sql ┘   (reads back SQL              (boots temp SQLite,
               for stable eids/ids)          migrate + seed, query → EDN)
```

The scraper today both **writes** `seed-*.sql` and **reads them back** (regex
parsing) to recover stable identifiers and curated fields across re-runs:

| Reader fn | Reads | Recovers |
|---|---|---|
| `assets/build-unit-name-eid-map` | `seed-<faction>-units.sql` | `[name eid faction]` |
| `unit-items-seed/build-unit-seed-id-map` | `seed-<faction>-units.sql` | `[name faction] → id` |
| `abilities-seed/build-ability-key-eid-map` | `seed-abilities.sql` | `key → eid` |
| `abilities-seed/build-spell-key-eid-map` | `seed-spells.sql` | `key → eid` |
| `subfactions-seed/build-slug->faction-id` | `seed-factions.sql` | `slug → faction-id` |
| `tables/build-lore-name->key-map` | `seed-lores.sql` | `suffix → lore-key` |

So the SQL seed is a **hybrid**: hand-authored rows (identities + curated fields)
plus machine-regenerated columns. The migration must move the curated half into
EDN as the new read-back source.

## Target pipeline (one hop)

```
seed/authoring/<v>/*.edn ─┐
                          ├─► rpfm-scraper ──► seed/datalog/<v>/*.edn   (merged, committed, loaded by API)
RPFM JSON ────────────────┘     (read curated + RPFM,
                                 emit generated, merge)
```

The integer `id` columns disappear: Datalog links by `eid` lookup-refs
(`[:unit/eid #uuid "…"]`), so unit-item / unit-mount junctions link by unit eid
instead of by row id. `build-unit-seed-id-map` (name→id) collapses into the
existing name→eid lookup.

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
    overrides.edn    (lore pins, display-name→key, card overrides — moved from code)
  datalog/<version>/        ← BUILD OUTPUT: authoring ⊕ generated, committed, loaded by the API
    (full set in `datalog-seed/seed-files` order)
```

`authoring/` is what a human edits to add a unit, fix a description, pin a lore.
`datalog/` is reproducible from `authoring/` + RPFM JSON; it is committed so the
API loads it with no build step, and so seed diffs are reviewable.

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

`derived-uuid` = `UUID/nameUUIDFromBytes` over `"/"`-joined parts (the same helper
the dump tool uses), so generated link/statistics eids are reproducible from the
curated identities with no stored state.

### Hybrid notes

- **spells / abilities**: the row identity is curated; the scraper overwrites
  only the RPFM-derived fields — spell `cost`, ability `name` + `cost`. Ability
  `description` is curated (RPFM's tooltip loc keys don't match ability keys, so
  it was preserved across scrapes), as is the full spell display. `is-unique` on
  units is curated (today `stats/apply-preserved` carries it across the rewrite).
- **units**: the curated skeleton is the per-faction unit list a human maintains.
  `key` (engine `land_units` key), `mark`, `lore`, and `family-name` are derived
  by the scraper (name-index match, `unit_set` junctions, name-suffix parsing)
  with curated overrides in `overrides.edn`.
- **unit-statistics** is hybrid: the numeric/string statline is RPFM-derived, but
  `abilities` and `draftable-spells` are **curated** — RPFM has no notion of which
  spells/abilities a unit may *draft*, and `stats/apply-preserved` carries them
  across scrapes today. They live in `authoring/<v>/unit-statistics.edn` keyed by
  `:unit-statistics/unit` (only units that have them); the scraper merges them
  onto the freshly computed statline.

  The scraper decomposes its engine-shaped statline into the
  `:unit-statistics/*` attributes using the `schema.us/fields` spec, which is
  owned by `rts-data-access`. To avoid the lean scraper depending on that
  component (and Datalevin), the `[doc-key attr kind]` vector is **replicated**
  in the scraper with a guard test asserting it equals `schema.us/fields`.

## eid stability

- Curated + hybrid-identity eids live in `authoring/` and are stable by
  construction.
- Generated eids are **deterministic functions** of curated identities (no SQL
  read-back needed): `derived-uuid`/UUID-v5 for links, subfactions, and
  statistics.
- **Known risk (pre-existing):** `items` and `mounts` eids are
  `sorted-index`-based, so adding/removing an item or mount **shifts** every
  later eid. This is unchanged from today's SQL pipeline; flagged here because it
  affects any FK that points at item/mount eids across patches. A follow-up could
  switch these to UUID-v5 on the engine key, but that's out of scope for the
  pipeline migration (it would rewrite existing eids).

## Merge

`rts-data` gains:

1. A **read API** the scraper uses instead of the regex SQL parsers:
   read curated authoring EDN (`read-authoring <version> <entity>`), exposing the
   identity/lookup maps the scraper needs (name→eid, key→eid, slug→faction-eid,
   lore-suffix→key).
2. A **merge** producing `datalog/<version>/` from curated authoring EDN ⊕
   scraper-generated EDN, in `datalog-seed/seed-files` transact order:
   - fully-curated files: copied whole;
   - hybrid files: authoring identity left-merged with generated fields by eid;
   - generated-only files: written as produced.

The loader (`rts-data.datalog-seed`) is unchanged — it keeps reading
`datalog/<version>/`.

## Phasing (beads under epic `rts-2lg`)

1. `rts-mz8` — this design doc + ownership split.
2. `rts-m8u` — one-time split: derive `authoring/<v>/` from the committed
   `datalog/8.0/` (curated whole; hybrids keep identity + curated fields).
3. `rts-wcw` — `rts-data` read API + curated⊕generated merge.
4. `rts-vxd` — scraper: units identity + `unit-statistics` EDN.
5. `rts-qfg` — scraper: items + mounts + unit-items + unit-mounts EDN.
6. `rts-47d` — scraper: subfactions + ability/spell field patches + unit-level-cost EDN.
7. `rts-904` — wire `core.clj` to the EDN writers + merge; delete
   `dump_datalog_seed.clj`; update CLAUDE.md.
8. `rts-o9o` — verify end-to-end (regenerated `datalog/` matches committed seed;
   poly check/test + e2e; page walk).

Gated on `rts-7xz`/`rts-sld` (separate epic): delete `seed-*.sql` + game-data
migrations once runtime stops reading SQLite game data.
```
