# Missing MP-mount units

`scripts/update_from_rpfm.py` generates `seed-mounts.sql` and
`seed-unit-mounts.sql` from RPFM `units_custom_battle_mounts_tables` — the
authoritative WH3 Custom Battle / MP army-builder mount list. Cost diffs
come from `main_units_tables.multiplayer_cost` (mounted variant − base).

Coverage is complete on the mount side (113 MP mounts, 657 unit_mount
rows, zero icon-stem misses). The only remaining gaps are **13 units**
that appear in game data as mounted variants but don't exist in our
`seed-<faction>-units.sql` files — so there's nowhere to attach their
mount rows. Adding the base unit to the faction seed will automatically
pull in the associated mounts on the next script run.

---

## Missing units (13)

| Faction | Unit | Available mounts |
|---|---|---|
| empire | Amethyst Wizard | Imperial Pegasus, Barded Warhorse |
| kislev | Captain | Barded Warhorse |
| kislev | Celestial Wizard | Warhorse |
| kislev | General | Barded Warhorse |
| norsca | Chaos Sorcerer (Metal) | Warhorse |
| norsca | Chaos Sorcerer Lord (Metal) | Warhorse |
| lizardmen | Slann Mage-Priest | *(placeholder icon — campaign-only)* |
| vampire-coast | Vampire Fleet Admiral (Pistol – Death) | Rotting Promethean |
| vampire-coast | Vampire Fleet Admiral (Pistol – Deep) | Rotting Promethean |
| vampire-coast | Vampire Fleet Admiral (Pistol – Vampires) | Rotting Promethean |
| vampire-coast | Vampire Fleet Admiral (Polearms – Death) | Rotting Promethean |
| vampire-coast | Vampire Fleet Admiral (Polearms – Deep) | Rotting Promethean |
| vampire-coast | Vampire Fleet Admiral (Polearms – Vampires) | Rotting Promethean |

Notes:

- **Amethyst Wizard (empire)** — exists in RPFM as `wh2_pro07_emp_cha_wizard_death_*`
  (a pre-release DLC variant of the generic Empire Wizard lineup). Not
  currently in our Empire seed. Safe to add if the unit is MP-accessible
  in the current patch.
- **Kislev Captain / Celestial Wizard / General** — these look like
  pre-DLC generic lord/hero slots (`wh_main_ksl_cha_*`) that ship in the
  base game but aren't in our Kislev seed, which focuses on DLC-24
  content. Low priority.
- **Norsca Chaos Sorcerer (Metal)** / **Chaos Sorcerer Lord (Metal)** —
  Metal lore variants specific to Norsca. We may intentionally exclude
  Metal from Norsca's spell lore list, in which case these should stay
  unseeded.
- **Lizardmen Slann Mage-Priest** — the base
  `wh2_main_lzd_cha_slann_mage_priest_campaign_0` uses a `placeholder`
  icon in `units_custom_battle_mounts_tables`. This is the known
  ambiguity already flagged by the script's lizardmen faction warning
  ("4 units not matched in loc: Slann Mage-Priest (Beasts/Death/Metal/Shadows)").
  Resolving it requires splitting the Slann into its lore-specific
  variants in our seed.
- **Vampire Fleet Admiral loadout variants (6)** — the Fleet Admiral has
  per-weapon / per-lore variants (`Pistol – Death`, `Polearms – Vampires`, …)
  that each map to a separate mounted-unit row. We currently seed a
  consolidated Fleet Admiral. If granular loadouts are worth modelling,
  seed one row per variant; otherwise either (a) leave the mount rows
  dropped or (b) collapse all 6 loadouts to a single `Vampire Fleet Admiral`
  row so the shared Rotting Promethean mount survives.

---

## Remediation

Add the missing unit row to the appropriate `seed-<faction>-units.sql`
and re-run:

```bash
python3 scripts/update_from_rpfm.py --data-dir scripts/rpfm_data
```

The script's `unit_id_map` is built from the seed files, so any unit
added to a faction seed will be picked up automatically on the next run
and its `unit_mount` rows will populate from `units_custom_battle_mounts_tables`.

No Python-script changes are needed for any of these gaps — the seed
files are the constraint.
