# Draft Rules — RPFM Cap Data Handoff

This document is a task brief for Claude Code (with the RPFM MCP server active) to scrape
per-unit and categorical cap data from WH3 game files and format it for use in
`components/rts-domain/src/com/devereux_henley/rts_domain/rules/draft.clj`.

---

## Background

The draft rules engine (`rules/draft.clj`) contains a hard-coded `caps` map:

```clojure
(def ^:private caps
  {:lord-max          1
   :hero-max          2
   :semc-wm-max       4
   :semc-wm-categories #{"Hero" "Monster" "War Machine" "Artillery" "Monstrous Cavalry"}
   :per-unit-max      2
   :unique-unit-max   1
   :section-slot-max  20})
```

These values are estimates based on typical WH3 competitive tournament rules.
The RPFM tables contain the authoritative values used by the game's multiplayer mode.

---

## Task: Scrape and verify cap values

Set the RPFM game to `warhammer_3` with `rebuild_dependencies: true`, then decode each
table below from `GameFiles` and inspect the relevant columns.

### 1. Per-unit caps (`per-unit-max`, `unique-unit-max`)

**RPFM path:** `db/multiplayer_unit_caps_tables/data__`

Expected columns:
- `game_mode` — the multiplayer mode name (e.g. `wh3_land_battle`, `wh3_domination`)
- `unit_key` — land unit key matching `land_units_tables`
- `max_num` — maximum number of this unit allowed in an army

**What to do:**
1. Decode the table and save to `scripts/rpfm_data/multiplayer_unit_caps_tables.json`
2. For each game mode, report:
   - The default cap that applies to most units (usually 2)
   - Any units capped at 1 (these are effectively `unique` — set `is_unique: true` in their seed data)
   - Any units with a higher cap (> 2)

**Output format:**

```
Default per-unit cap for wh3_land_battle: 2
Units capped at 1 (unique):
  - wh_main_emp_lord_karl_franz
  - wh3_dlc25_emp_lord_boris_todbringer
  ...
Units capped > 2:
  (none found / list them)
```

### 2. Categorical caps (`hero-max`, `lord-max`, `semc-wm-max`)

**RPFM path:** `db/multiplayer_category_caps_tables/data__`
(if this table doesn't exist, try `db/multiplayer_army_caps_tables/data__`)

Expected columns:
- `game_mode` — multiplayer mode name
- `unit_category` — category key (e.g. `lord`, `hero`, `monster`)
- `max_num` — maximum of this category allowed in an army

**What to do:**
1. Decode the table and save to `scripts/rpfm_data/multiplayer_category_caps_tables.json`
2. For each game mode, report the `max_num` for:
   - `lord` (or equivalent key)
   - `hero` (or equivalent key)
   - Any combined cap rule that covers the "Single Entity Characters, Monsters, and War Machines" pool

**Output format:**

```
wh3_land_battle:
  lord: 1
  hero: 2
  single_entity_combined: 4 (categories: hero, monster, war_machine, artillery)
```

### 3. "Single Entity" classification

**Goal:** verify which unit categories count toward the combined
`semc-wm-max` cap (currently `#{"Hero" "Monster" "War Machine" "Artillery" "Monstrous Cavalry"}`).

**RPFM paths to check:**
- `db/main_units_tables/data__` — column `unit_category` links to unit category keys
- `db/land_units_tables/data__` — column `num_mounts_in_formation` or `is_large` may help
  identify single-entity units (num_mounts_in_formation = 1 for characters and large creatures)

**What to do:**
1. List the distinct `unit_category` values where units are single-entity (size 1 model).
2. Cross-reference against the categories in this codebase
   (`components/rts-data/resources/rts-data/sql/seed/seed-unit-categories.sql`):
   Lord, Hero, Melee Infantry, Missile Infantry, Melee Cavalry, Missile Cavalry,
   Chariot, War Machine, Artillery, War Beast, Monstrous Infantry, Monster, Monstrous Cavalry
3. Confirm which of those categories belong in `semc-wm-categories`.

### 4. Slot cap (`section-slot-max`)

**RPFM path:** `db/multiplayer_army_slot_caps_tables/data__` (or equivalent)

**What to do:**
Confirm whether the 20-slot-per-section cap is hard-coded in the game or configurable
per game mode. Report the value.

---

## Output

After scraping, produce a replacement `caps` map for `rules/draft.clj`:

```clojure
;; Verified from RPFM wh3_land_battle / wh3_domination tables — <date>
(def ^:private caps
  {:lord-max          <N>
   :hero-max          <N>
   :semc-wm-max       <N>
   :semc-wm-categories #{<"CategoryName" ...>}
   :per-unit-max      <N>
   :unique-unit-max   1   ;; always 1 — unique units are identified by is_unique flag
   :section-slot-max  <N>})
```

Also produce a list of any units whose `is_unique` flag in `unit_statistics` JSON should be
set to `true` (i.e. units found in `multiplayer_unit_caps_tables` with `max_num = 1`).
These should be added manually to the relevant `seed-<faction>-units.sql` files.
