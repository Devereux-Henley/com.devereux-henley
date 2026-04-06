# Game Data

## Overview

Unit statistics, spell costs, and related seed data are sourced directly from the WH3 game files using [RPFM](https://github.com/Frodo45127/rpfm) (Rusted PackFile Manager). After each game patch, the seed SQL files must be refreshed to reflect the latest balance changes.

The update script is `scripts/update_from_rpfm.py`. It reads RPFM-decoded game tables from `scripts/rpfm_data/` and rewrites the `unit_statistics` JSON blob for every unit in every faction seed file, and updates spell gold costs in `seed-spells.sql`.

Non-numeric fields (`abilities`, `draftable-spells`, `mounts`) are preserved from the existing seed and are not overwritten.

---

## What gets updated

| Seed file(s) | Fields updated |
|---|---|
| `seed-<faction>-units.sql` | `cost`, `is_large`, `unit_size`, `health`, `barrier`, `armor`, `leadership`, `speed`, `melee_attack`, `melee_attack_types`, `melee_defence`, `weapon_strength`, `weapon_damage`, `weapon_ap_damage`, `charge_bonus`, `ammunition`, `range`, `missile_damage`, `missile_base_damage`, `missile_ap_damage`, `missile_damage_types` |
| `seed-spells.sql` | `gold_cost` |

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

Each decoded file must be in the RPFM MCP output format: a JSON array with a single `{type, text}` element where `text` is the serialised `DBRFileInfo` or `LocRFileInfo` object.

### 2. Run the update script

```bash
python3 scripts/update_from_rpfm.py --data-dir scripts/rpfm_data
```

The script prints a per-faction summary. Any units whose display name could not be matched to a game key are listed as warnings and left unchanged.

Use `--dry-run` to preview changes without writing files.

### 3. Review and commit

```bash
git diff components/rts-data/resources/rts-data/sql/seed/
```

Verify that the stat changes look plausible (costs, armor, weapon strength, etc. matching the patch notes), then commit.

---

## Known limitations

- **4 Lizardmen Slann variants** (`Slann Mage-Priest (Beasts/Death/Metal/Shadows)`) share the same display name in-game and are not matched; their stats must be updated manually if changed.
- `abilities`, `draftable-spells`, and `mounts` are not sourced from game data — they must be maintained manually when CA adds or renames abilities for a unit.
- `seed-spells.sql` `mana_cost` and spell descriptions are not updated by this script.
