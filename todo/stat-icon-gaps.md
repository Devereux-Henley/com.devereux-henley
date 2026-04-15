# Stat icon gaps

Known incomplete coverage introduced with the `attack-modifier` / `attributes` scrape
(`bases/rpfm-scraper/src/com/devereux_henley/rpfm_scraper/assets.clj`,
`components/rts-domain/src/com/devereux_henley/rts_domain/handlers/draft.clj`).

## Missing attribute icons

The following unit-attribute keys from `unit_attributes_tables` are in
`stat-icon-sources` but have no matching PNG under
`ui/battle ui/ability_icons/<key>.png`. Units that carry these attributes
render a broken `<img>` for that badge (the `onerror` trick is not wired here).

- `disciplined`
- `fanatic`
- `fatigue_res`
- `ignore_imbue_contact_effects_ally`
- `invulnerable_to_effects_ally`
- `invulnerable_to_effects_enemy`
- `melee_disabled`
- `ogre_charge_upgraded`
- `shoot_disabled`

Resolution options:

1. Drop these keys from `stat-icon-sources` and filter them out of the
   `:attributes` vector in `parse-unit-statistics` so they never reach the
   template.
2. Pick fallback icons from a related existing file (e.g. reuse
   `fatigue_immune.png` for `fatigue_res`).
3. Add an `onerror="this.style.display='none'"` to `.draft-attribute-icon`
   img tags so broken ones simply vanish.

## Contact-phase → modifier-slug ambiguities

`contact-phase-slug-map` in `bases/rpfm-scraper/src/com/devereux_henley/rpfm_scraper/tables.clj`
maps every `melee_weapons.contact_phase` and `projectiles.contact_stat_effect`
key to the closest CA `modifier_icon_*.png`. A few mappings are best-effort
because the source effect has no perfect icon analogue:

| contact key | mapped slug | note |
|---|---|---|
| `wh3_dlc25_unit_contact_erosion` | `flammable` | Erosion is a stone/wall-damage DoT; `flammable` is the nearest icon. |
| `wh3_dlc26_unit_contact_sticky_webs` | `dazed` | Webs immobilise; we use the `dazed` glyph. |
| `wh2_dlc15_unit_contact_rattled` | `dazed` | Rattled is a morale debuff; could also use `overhead_morale`. |
| `wh2_dlc16_unit_contact_weakened` | `blinded` | Weakened reduces attack; no exact icon exists. |
| `wh2_dlc17_unit_contact_slow_death` | `soulblight` | Bleed-style DoT; reusing the soul-drain glyph. |
| `wh3_dlc24_unit_contact_dark_blood` | `blood` | |
| `wh3_dlc25_unit_contact_suffocating` | `blinded` | Unique effect, no dedicated icon. |
| `wh2_main_unit_contact_weeping_blade` | `poison` | Weeping blade applies poison-like DoT. |
| `wh2_main_unit_contact_souls` | `soulblight` | |
| `wh2_pro08_unit_contact_dampen` | `dazed` | |
| `wh2_dlc11_unit_contact_charmed` | `dazed` | |
| `wh2_dlc11_unit_contact_disrupted` | `dazed` | |
| `wh2_dlc11_unit_contact_monstrous_impact` | `dazed` | Knockdown — no dedicated icon. |
| `wh3_dlc26_contact_phase_inevitable_end` | `soulblight` | |
| `wh3_dlc26_unit_abilities_acid_vomit_debuff` | `flammable` | Acid corrosion — nearest match. |
| `wh3_main_unit_contact_soporific_musk` | `dazed` | Slaanesh musk debuff. |

If any of these bother you the fix is a one-line edit in
`contact-phase-slug-map`. We could also scrape additional modifier icons if CA
ships new `modifier_icon_*.png` files for erosion/webs/weakened in a future
patch.

## Health formula inflates vehicle / artillery HP

`stats.clj` now computes `health = (battle_entities.hit_points +
land_units.bonus_hit_points) * main_units.num_men`, which matches in-game
values for infantry, cavalry, and monstrous units:

- Empire Spearmen: 8280
- Empire Knights RoR: 6000
- Reiksguard Knights: 6000+
- Ironbreakers: 9600

It **overshoots** for units where CA stores HP per crew entity but the unit
is conceptually a single vehicle:

| Unit | Computed | Real in-game |
|---|---|---|
| Steam Tank | 27 180 | ~9 060 (num_men=3 crew, should count as 1) |
| Thunderbarge (Grungni) | 358 092 | ~12 348 (num_men=29 but single ship) |
| Dwarf Goblin Hewer | ~500 000 | ~2 000 (num_men=40 crew + 4 engines) |
| Other `war_machine` / `war_beast` vehicles | 10–30× | actual |

The correct formula for these is roughly `per_entity_hp × 1` (for `chariot` /
single-vehicle classes) or `engine_hp × num_engines` (for crewed artillery).
Detecting which model to use requires inspecting `land_units.category` /
`class` or `main_units.is_monstrous` plus `num_engines`, and the reverse-
engineered model isn't documented by CA.

Options:

1. Add a per-category override: if `category ∈ {war_machine, war_beast}` or
   `class = chariot` or `num_engines > 0`, skip the `× num_men` multiplier
   and use `per_entity_hp` directly (or `engine_hp × num_engines`).
2. Scrape the game's computed HP from somewhere (there may be a table we
   haven't found yet).
3. Accept the overshoot and document — the HP bar becomes meaningless for
   vehicles but the rest of the stats are correct.

## Template slot reservation

`.draft-stat-bar-row` uses a CSS grid with a fixed 44px badge column
(`grid-template-columns: 24px 44px 2.5rem 1fr`). That column fits **two**
16–18px badges; if a row ever needs a third we'll either truncate or widen.
Current data rarely exceeds two — `damage-types` and `attack-modifiers` are
each 0–2 entries in practice.
