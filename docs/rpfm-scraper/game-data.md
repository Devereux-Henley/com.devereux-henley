# Game Data

## Overview

Unit statistics, spell costs, and related seed data are sourced directly from the WH3 game files using [RPFM](https://github.com/Frodo45127/rpfm) (Rusted PackFile Manager). After each game patch, the seed SQL files must be refreshed to reflect the latest balance changes.

The update tool is the `rpfm-scraper` base (`bases/rpfm-scraper`), invoked from the repo root. It reads RPFM-decoded game tables from `bases/rpfm-scraper/data/` and rewrites the `unit_statistics` JSON blob for every unit in every faction seed file, and updates spell gold costs in `seed-spells.sql`.

Non-numeric fields (`abilities`, `draftable-spells`, `mounts`) are preserved from the existing seed and are not overwritten.

---

## What gets updated

| Seed file(s) | Fields updated |
|---|---|
| `seed-<faction>-units.sql` | `cost`, `is_large`, `unit_size`, `health`, `barrier`, `armor`, `leadership`, `speed`, `melee_attack`, `melee_attack_types`, `melee_defence`, `weapon_strength`, `weapon_damage`, `weapon_ap_damage`, `charge_bonus`, `ammunition`, `range`, `missile_damage`, `missile_base_damage`, `missile_ap_damage`, `missile_damage_types`, `equipment` (lords/heroes only) |
| `seed-spells.sql` | `cost` |
| `seed-abilities.sql` | `name`, `description`, `cost` (`additional_melee_cp + additional_missile_cp` from `unit_special_abilities_tables`) |
| `seed-items.sql` | fully regenerated: all MP ancillaries with `key`, `name`, `category`, `cost`, `icon_key` (dedupe stem) |
| `seed-unit-items.sql` | fully regenerated: unit → item links for legendary lords/heroes with pre-assigned gear |
| `seed-mounts.sql` | fully regenerated from `units_custom_battle_mounts_tables`: one row per distinct MP mount, keyed on icon stem (e.g. `mount_barded_warhorse`) |
| `seed-unit-mounts.sql` | fully regenerated: unit → mount links with cost = `main_units_tables.multiplayer_cost` diff (mounted variant − base) |
| `seed-unit-level-cost.sql` | fully regenerated from `unit_stats_land_experience_bonuses_tables`: one row per veteran rank (0-9) with `fixed_cost`, `cost_multiplier`, fatigue, and combat-potential deltas. Engine formula: `adjusted_cost = round(base_cost * cost_multiplier) + fixed_cost` |
| `asset/icon/ability/*.png` | spell icons copied alongside ability icons when `--icons-dir` is given (spells are abilities in WH3; icons keyed by spell eid) |
| `asset/icon/item/*.png` | item icons copied when `--item-icons-dir` is given (one file per distinct `ancillary_types_tables.ui_icon` stem) |
| `asset/icon/mount/*.png` | mount icons copied when `--mount-icons-dir` is given (one file per distinct mount icon stem, matching `mount.icon_key`) |

---

## Refresh workflow

### 1. Decode game tables via RPFM MCP

Open Claude Code with the RPFM MCP server active, set the game to `warhammer_3` with `rebuild_dependencies: true`, and decode each table below from `GameFiles`. Save each result to the corresponding file in `bases/rpfm-scraper/data/` (gitignored — these files are regenerated from the game install on demand and are not committed).

| File | RPFM path |
|---|---|
| `land_units_tables.json` | `db/land_units_tables/data__` |
| `main_units_tables.json` | `db/main_units_tables/data__` |
| `melee_weapons_tables.json` | `db/melee_weapons_tables/data__` |
| `missile_weapons_tables.json` | `db/missile_weapons_tables/data__` |
| `battle_entities_tables.json` | `db/battle_entities_tables/data__` |
| `projectiles_tables.json` | `db/projectiles_tables/data__` |
| `unit_special_abilities_tables.json` | `db/unit_special_abilities_tables/data__` |
| `unit_armour_types_tables.json` | `db/unit_armour_types_tables/data__` |
| `land_units_loc.json` | `text/db/land_units__.loc` |
| `agent_subtypes_tables.json` | `db/agent_subtypes_tables/data__` |
| `ancillaries_included_agent_subtypes_tables.json` | `db/ancillaries_included_agent_subtypes_tables/data__` |
| `ancillaries_tables.json` | `db/ancillaries_tables/data__` |
| `ancillaries_loc.json` | `text/db/ancillaries__.loc` |
| `ancillary_types_tables.json` | `db/ancillary_types_tables/data__` |
| `units_custom_battle_mounts_tables.json` | `db/units_custom_battle_mounts_tables/data__` |
| `mounts_tables.json` | `db/mounts_tables/data__` |
| `battlefield_engines_tables.json` | `db/battlefield_engines_tables/data__` |
| `unit_attributes_to_groups_junctions_tables.json` | `db/unit_attributes_to_groups_junctions_tables/data__` |
| `unit_stats_land_experience_bonuses_tables.json` | `db/unit_stats_land_experience_bonuses_tables/data__` |
| `unit_sets_tables.json` | `db/unit_sets_tables/data__` |
| `unit_set_to_unit_junctions_tables.json` | `db/unit_set_to_unit_junctions_tables/data__` |

Stat icons (pass via `--stat-icons-dir`) are extracted from `ui/skins/default/` as a sibling directory tree — use `mcp__rpfm__extract_packed_files` with the following GameFiles paths:

- `ui/skins/default/icon_mancount.png`
- `ui/skins/default/icon_stat_armour.png`
- `ui/skins/default/icon_stat_morale.png`
- `ui/skins/default/icon_stat_speed.png`
- `ui/skins/default/icon_stat_attack.png`
- `ui/skins/default/icon_stat_defence.png`
- `ui/skins/default/icon_stat_damage_base.png`
- `ui/skins/default/icon_stat_charge_bonus.png`
- `ui/skins/default/icon_stat_ammo.png`
- `ui/skins/default/icon_stat_range.png`
- `ui/skins/default/icon_stat_ranged_damage_base.png`
- `ui/skins/default/icon_stat_health.png`
- `ui/skins/default/modifier_icon_flaming.png`
- `ui/skins/default/modifier_icon_magical.png`

Each decoded file must be in the RPFM MCP output format: a JSON array with a single `{type, text}` element where `text` is the serialised `DBRFileInfo` or `LocRFileInfo` object.

### 2. Run the update tool

```bash
clojure -M:dev -m com.devereux-henley.rpfm-scraper.core --data-dir bases/rpfm-scraper/data
```

Or, after building the uber JAR with `clojure -M:build -A rpfm-scraper uber`:

```bash
java -jar target/rpfm-scraper.jar --data-dir bases/rpfm-scraper/data
```

The tool prints a per-faction summary. Any units whose display name could not be matched to a game key are listed as warnings and left unchanged.

Use `--dry-run` to preview changes without writing files. Pass `--strict` to fail the run when any unit row lacks a `key`, lacks an `<eid>.png` in `asset/card/unit/`, or any PNG in that directory no longer corresponds to a unit row. Strict is what the periodic data-refresh job runs so coverage regressions show as a red build.

Every run (strict or not) writes a coverage manifest to `target/scraper-coverage.json`:

```json
{
  "total": 2200,
  "missing-keys":  ["Display Name A", "Display Name B"],
  "missing-icons": ["Display Name C"],
  "stale-pngs":    ["00010024-0000-4000-8000-000000000000"]
}
```

`missing-keys` and `missing-icons` are sorted, deduplicated unit display names. `stale-pngs` is the list of `<eid>` filenames in `asset/card/unit/` whose `eid` is no longer in any seed file (the bidirectional check that catches PNGs left over from a deleted/renamed unit). The `placeholder.png` filename is exempt.

When a `--strict` run fails, the manifest is the input list for extending `bases/rpfm-scraper/src/.../overrides.clj` — add a `display-name → icon-stem` entry for each `missing-keys`/`missing-icons` name the heuristic could not resolve, then re-run.

#### Optional: copy game icons and unit cards

RPFM-extract the relevant folders from the game files and place them under `bases/rpfm-scraper/assets/` (gitignored), preserving the game's internal folder structure. Then pass the leaf / extraction-root directories:

| Arg | Source folder in game files | Writes to |
|---|---|---|
| `--icons-dir` | `ui/abilities/` | `asset/icon/ability/` (abilities **and** spells) |
| `--item-icons-dir` | extraction root containing `ui/` (items reference `ui/campaign ui/ancillaries/`, `ui/skins/default/`, etc.) | `asset/icon/item/` |
| `--mount-icons-dir` | extraction root containing `ui/` (same dir as `--item-icons-dir`; mounts are in `ui/campaign ui/mounts/`) | `asset/icon/mount/` |
| `--unit-cards-dir` | `ui/units/icons/` | `asset/card/unit/` |
| `--portraits-dir` | `ui/portraits/units/no_culture/` | `asset/card/unit/` (fallback for units without a card) |

```bash
clojure -M:dev -m com.devereux-henley.rpfm-scraper.core \
  --data-dir        bases/rpfm-scraper/data \
  --icons-dir       "bases/rpfm-scraper/assets/ability-icons/ui/battle ui/ability_icons" \
  --item-icons-dir  bases/rpfm-scraper/assets/items \
  --mount-icons-dir bases/rpfm-scraper/assets/items \
  --unit-cards-dir  bases/rpfm-scraper/assets/cards/ui/units/icons \
  --portraits-dir   bases/rpfm-scraper/assets/portraits/ui/portraits/units/no_culture
```

Each flag is independent — omit any you don't have extracted. Copied PNGs are trimmed of transparent borders via `mogrify` (ImageMagick), which must be on `PATH`.

Spell icons are sourced from the same `--icons-dir` as abilities (WH3 stores spells as abilities internally). Spell icons are written alongside ability icons in `asset/icon/ability/` since templates resolve both via the `/icon/ability/` path.

Mount icons are keyed by icon stem (e.g. `mount_barded_warhorse.png`), matching the `mount.icon_key` column populated in `seed-mounts.sql`. Templates resolve them via `mount.icon-key`.

### 3. Review and commit

```bash
git diff components/rts-data/resources/rts-data/sql/seed/
```

Verify that the stat changes look plausible (costs, armor, weapon strength, etc. matching the patch notes), then commit.

---

## Known limitations

- **4 Lizardmen Slann variants** (`Slann Mage-Priest (Beasts/Death/Metal/Shadows)`) share the same display name in-game and are not matched by the loc heuristic; the `display-name-unit-key-overrides` map in `overrides.clj` now pins each variant to its engine key so they no longer appear in `scraper-coverage.json` `missing-keys`, but stat-blob updates for these four still depend on the override pointing at a current `main_units_tables` row.
- `abilities` and `draftable-spells` are not sourced from game data — they must be maintained manually when CA adds or renames abilities for a unit. (Mounts **are** sourced from game data via `units_custom_battle_mounts_tables` as of the 000021 / 000022 migrations.)
- `equipment` is populated only for legendary lords/heroes with character-specific items in `ancillaries_included_agent_subtypes_tables`. Generic lords/heroes (non-legendary) have no `equipment` field — their item pools are defined by the game's faction/category system and are not stored per-unit.
- `seed-spells.sql` `mana_cost` and spell descriptions are not updated by this script.
- **Missing MP-mount units (13)** — some units that appear as mounted variants in `units_custom_battle_mounts_tables` (e.g. Amethyst Wizard, pre-DLC Kislev heroes, Vampire Fleet Admiral loadout variants) aren't in our faction seed files and therefore get no `unit_mount` rows. See `todo/missing-mp-mount-units.md` for the list and resolution notes.

---

## MP item availability — research findings

### What we tried

We investigated whether the per-unit item pool shown in the WH3 in-game MP army builder could be derived from RPFM game data. The goal was to auto-populate `seed-unit-items.sql` for generic lords and heroes (e.g. Empire Captain) whose items are not pre-assigned via `ancillaries_included_agent_subtypes_tables`.

Every ancillary-related DB table was searched:

| Table | What it contains | Useful? |
|---|---|---|
| `ancillaries_tables` | Item definitions; `faction_set` field groups items by campaign faction availability | No — `faction_set` is a campaign concept, not an MP filter |
| `ancillaries_included_agent_subtypes_tables` | Items pre-assigned to specific agent subtypes | Only mounts for generic heroes |
| `ancillary_to_included_agents_tables` | Items linked to agent types (`general`, `champion`, etc.) | Campaign followers/mounts only; no MP equipment |
| `ancillaries_categories_agent_subtype_override_junctions_tables` | Per-subtype overrides for allowed ancillary categories | Only two DLC-specific entries |
| `ancillary_set_ancillary_junctions_tables` | Named item sets for legendary lords | Legendary lords only |
| `ancillaries_categories_faction_junctions_tables` | Ancillary category availability per faction | Campaign only |

Lua scripts in `data_script.pack` were also searched. No script references the specific item keys (`wh_main_anc_weapon_sword_of_anti-heroes`, etc.) and there are no MP army builder scripts in the pack.

A raw byte search of `data_script.pack`, `boot.pack`, and `data.pack` confirmed these item keys do not appear anywhere outside their own definition rows in `ancillaries_tables`.

### Conclusion

**MP item eligibility for generic heroes is enforced by the C++ game engine, not by any data-driven file.** The engine reads `ancillaries_tables` and applies rules that are not exposed through RPFM or the Lua scripting layer.

### Approach going forward

The `unit_item` table supports manual curation: for each unit that has a defined item pool in the in-game MP builder, add rows to the relevant `seed-<faction>-units.sql` or a dedicated seed file associating the unit's DB id with the item DB ids from `seed-items.sql`.

The item keys and costs are available in `seed-items.sql` (regenerated by `update_from_rpfm.py` after each patch). Item DB ids are assigned by sorted key order and are stable across runs as long as CA does not rename or remove an item.

---

## MP mount availability — resolved

Unlike items, MP mount availability **is** stored in a data-driven file:
`db/units_custom_battle_mounts_tables`. WH3 calls its MP army builder
"Custom Battle" internally, and this table directly enumerates every
`(base_unit, mounted_unit, icon_name)` combination selectable in it.

- **Mount identity** — each row's `icon_name` basename (e.g.
  `mount_barded_warhorse`) is the stable mount key; one `mount` row per
  distinct stem. This matches the on-disk icon filename exactly, so
  templates resolve to `/icon/mount/{{mount.icon-key}}.png`.
- **Mount cost** — computed from `main_units_tables.multiplayer_cost`:
  `cost = mounted_variant.mp_cost − base_variant.mp_cost`, giving the
  correct MP add-on cost.
- **Mount display name** — resolved by cross-referencing the icon stem
  against `ancillary_types_tables.ui_icon` and looking up the matching
  ancillary's localised name from `ancillaries_loc`. For MP-only mounts
  that have no ancillary (rare), the script falls back to title-casing
  the stem.

The earlier curated `unit_statistics.mounts` JSON approach was removed
— it stored `main_units_tables.multiplayer_cost` as the mount cost
(the variant's TOTAL price) instead of the add-on diff, which caused
the UI to double-count the base unit cost.
