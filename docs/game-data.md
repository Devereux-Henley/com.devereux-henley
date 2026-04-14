# Game Data

## Overview

Unit statistics, spell costs, and related seed data are sourced directly from the WH3 game files using [RPFM](https://github.com/Frodo45127/rpfm) (Rusted PackFile Manager). After each game patch, the seed SQL files must be refreshed to reflect the latest balance changes.

The update script is `scripts/update_from_rpfm.py`. It reads RPFM-decoded game tables from `scripts/rpfm_data/` and rewrites the `unit_statistics` JSON blob for every unit in every faction seed file, and updates spell gold costs in `seed-spells.sql`.

Non-numeric fields (`abilities`, `draftable-spells`, `mounts`) are preserved from the existing seed and are not overwritten.

---

## What gets updated

| Seed file(s) | Fields updated |
|---|---|
| `seed-<faction>-units.sql` | `cost`, `is_large`, `unit_size`, `health`, `barrier`, `armor`, `leadership`, `speed`, `melee_attack`, `melee_attack_types`, `melee_defence`, `weapon_strength`, `weapon_damage`, `weapon_ap_damage`, `charge_bonus`, `ammunition`, `range`, `missile_damage`, `missile_base_damage`, `missile_ap_damage`, `missile_damage_types`, `equipment` (lords/heroes only) |
| `seed-spells.sql` | `cost` |
| `seed-abilities.sql` | `name`, `description`, `cost` (`additional_melee_cp + additional_missile_cp` from `unit_special_abilities_tables`) |
| `seed-items.sql` | fully regenerated: all ancillaries with `key`, `name`, `category`, `cost` (`uniqueness_score`) |
| `seed-unit-items.sql` | fully regenerated: unit → item links for legendary lords/heroes with pre-assigned gear |
| `asset/icon/ability/*.png` | spell icons copied alongside ability icons when `--icons-dir` is given (spells are abilities in WH3; icons keyed by spell eid) |
| `asset/icon/item/*.png` | item icons copied when `--item-icons-dir` is given (keyed by item eid) |
| `asset/icon/mount/*.png` | mount icons copied when `--mount-icons-dir` is given (keyed by display-name slug, e.g. `barded_warhorse.png`) |

---

## Refresh workflow

### 1. Decode game tables via RPFM MCP

Open Claude Code with the RPFM MCP server active, set the game to `warhammer_3` with `rebuild_dependencies: true`, and decode each table below from `GameFiles`. Save each result to the corresponding file in `scripts/rpfm_data/`.

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

Each decoded file must be in the RPFM MCP output format: a JSON array with a single `{type, text}` element where `text` is the serialised `DBRFileInfo` or `LocRFileInfo` object.

### 2. Run the update script

```bash
python3 scripts/update_from_rpfm.py --data-dir scripts/rpfm_data
```

The script prints a per-faction summary. Any units whose display name could not be matched to a game key are listed as warnings and left unchanged.

Use `--dry-run` to preview changes without writing files.

#### Optional: copy game icons

Extract the relevant folders from the game files (e.g. via RPFM's Extract → to disk), then pass the extracted paths:

| Arg | Source folder in game files | Writes to |
|---|---|---|
| `--icons-dir` | `ui/abilities/` | `asset/icon/ability/` (abilities **and** spells) |
| `--item-icons-dir` | extraction root containing `ui/` (items reference multiple subfolders: `ui/campaign ui/ancillaries/`, `ui/skins/default/`, etc.) | `asset/icon/item/` |
| `--mount-icons-dir` | extraction root containing `ui/` (same dir as `--item-icons-dir`; mounts are in `ui/campaign ui/mounts/`) | `asset/icon/mount/` |

```bash
python3 scripts/update_from_rpfm.py \
  --data-dir scripts/rpfm_data \
  --icons-dir "/path/to/extracted/ui/battle ui/ability_icons" \
  --item-icons-dir /path/to/extracted \
  --mount-icons-dir /path/to/extracted
```

Spell icons are sourced from the same `--icons-dir` as abilities (WH3 stores spells as abilities internally). Spell icons are written alongside ability icons in `asset/icon/ability/` since templates resolve both via the `/icon/ability/` path.

Mount icons are keyed by display-name slug (e.g. `barded_warhorse.png`) so templates can look them up using `mount.name`.

### 3. Review and commit

```bash
git diff components/rts-data/resources/rts-data/sql/seed/
```

Verify that the stat changes look plausible (costs, armor, weapon strength, etc. matching the patch notes), then commit.

---

## Known limitations

- **4 Lizardmen Slann variants** (`Slann Mage-Priest (Beasts/Death/Metal/Shadows)`) share the same display name in-game and are not matched; their stats must be updated manually if changed.
- `abilities`, `draftable-spells`, and `mounts` are not sourced from game data — they must be maintained manually when CA adds or renames abilities for a unit.
- `equipment` is populated only for legendary lords/heroes with character-specific items in `ancillaries_included_agent_subtypes_tables`. Generic lords/heroes (non-legendary) have no `equipment` field — their item pools are defined by the game's faction/category system and are not stored per-unit.
- `seed-spells.sql` `mana_cost` and spell descriptions are not updated by this script.

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
