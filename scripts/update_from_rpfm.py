#!/usr/bin/env python3
"""
Updates all seed SQL data from RPFM-decoded WH3 game files.

Replaces update_unit_stats.py and update_spell_gold_costs.py, reading directly
from RPFM-decoded game tables instead of the stale twwstats.com API.

Usage:
  python3 scripts/update_from_rpfm.py --data-dir <path-to-rpfm-decoded-files>

The --data-dir should contain the RPFM-decoded JSON files named exactly as
produced by the RPFM MCP decode_packed_file tool for these tables:
  land_units_tables.json
  main_units_tables.json
  melee_weapons_tables.json
  battle_entities_tables.json
  missile_weapons_tables.json
  projectiles_tables.json
  unit_special_abilities_tables.json
  unit_abilities_tables.json
  land_units_loc.json
  unit_abilities_loc.json
  agent_subtypes_tables.json
  ancillaries_included_agent_subtypes_tables.json
  ancillaries_tables.json

To regenerate these files, use Claude Code with the RPFM MCP server and run
decode_packed_file for each table from GameFiles, then save the output to
the named files above.
"""

import argparse
import json
import os
import re
import shutil
import subprocess
import sys

SEED_DIR = "components/rts-data/resources/rts-data/sql/seed"

FACTION_KEY_MAP = {
    "empire":            ["emp"],
    "beastmen":          ["bst"],
    "bretonnia":         ["brt"],
    "chaos-dwarfs":      ["chd"],
    "daemons-of-chaos":  ["dae"],
    "dark-elves":        ["def"],
    "dwarfs":            ["dwf"],
    "grand-cathay":      ["cth"],
    "greenskins":        ["grn"],
    "high-elves":        ["hef"],
    "khorne":            ["kho"],
    "kislev":            ["kis", "ksl"],
    "lizardmen":         ["lzd"],
    "norsca":            ["nor"],
    "nurgle":            ["nur"],
    "ogre-kingdoms":     ["ogr"],
    "skaven":            ["skv"],
    "slaanesh":          ["sla"],
    "tomb-kings":        ["tmb"],
    "tzeentch":          ["tze"],
    "vampire-coast":     ["cst"],
    "vampire-counts":    ["vmp"],
    "warriors-of-chaos": ["chs", "woc"],
    "wood-elves":        ["wef"],
}


# ---------------------------------------------------------------------------
# Generic RPFM table parser
# ---------------------------------------------------------------------------

def _val(cell):
    """Extract the scalar value from an RPFM typed cell dict."""
    for v in cell.values():
        return v
    return None


def parse_rpfm_table(filepath):
    """
    Parse an RPFM-decoded table file.
    Returns (field_names: list[str], rows: list[dict]).
    """
    with open(filepath) as f:
        raw = json.load(f)
    # The decode output wraps in [{type, text}]
    if isinstance(raw, list) and raw and "text" in raw[0]:
        obj = json.loads(raw[0]["text"])
    else:
        obj = raw
    info = obj.get("DBRFileInfo") or obj.get("LocRFileInfo")
    entry = info[0]
    table = entry["table"]
    fields = [f["name"] for f in table["definition"]["fields"]]
    rows = []
    for row in table["table_data"]:
        rows.append({fields[i]: _val(cell) for i, cell in enumerate(row)})
    return fields, rows


def parse_loc_file(filepath):
    """
    Parse an RPFM-decoded .loc file.
    Returns dict {key: text}.
    """
    with open(filepath) as f:
        raw = json.load(f)
    if isinstance(raw, list) and raw and "text" in raw[0]:
        obj = json.loads(raw[0]["text"])
    else:
        obj = raw
    info = obj.get("LocRFileInfo")
    entry = info[0]
    table = entry["table"]
    result = {}
    for row in table["table_data"]:
        k = _val(row[0])
        t = _val(row[1])
        result[k] = t
    return result


# ---------------------------------------------------------------------------
# Build lookup maps from game tables
# ---------------------------------------------------------------------------

def build_armour_map(rows):
    """key -> armour_value (int)"""
    return {r["key"]: r["armour_value"] for r in rows}


def build_entity_map(rows):
    """key -> {hit_points, run_speed, size}"""
    return {r["key"]: {
        "hit_points": r["hit_points"],
        "run_speed": r["run_speed"],
        "size": r["size"],
    } for r in rows}


def build_melee_weapon_map(rows):
    """key -> {damage, ap_damage, is_magical, ignition_amount}"""
    return {r["key"]: {
        "damage": r["damage"],
        "ap_damage": r["ap_damage"],
        "is_magical": r["is_magical"],
        "ignition_amount": r.get("ignition_amount", 0) or 0,
    } for r in rows}


def build_missile_weapon_map(rows):
    """key -> default_projectile key"""
    return {r["key"]: r["default_projectile"] for r in rows}


def build_projectile_map(rows):
    """key -> {effective_range, damage, ap_damage, is_magical, ignition_amount}"""
    return {r["key"]: {
        "effective_range": r["effective_range"],
        "damage": r["damage"],
        "ap_damage": r["ap_damage"],
        "is_magical": r["is_magical"],
        "ignition_amount": r.get("ignition_amount", 0) or 0,
    } for r in rows}


def build_land_unit_map(rows, armour_map, entity_map, melee_map, missile_wep_map, projectile_map):
    """land_unit key -> computed stat dict"""
    result = {}
    for r in rows:
        key = r["key"]
        armour_val = armour_map.get(r["armour"] or "", 0)
        entity = entity_map.get(r["man_entity"] or "", {})
        melee = melee_map.get(r["primary_melee_weapon"] or "", {})

        missile_key = r.get("primary_missile_weapon") or ""
        proj_key = missile_wep_map.get(missile_key, "") if missile_key else ""
        proj = projectile_map.get(proj_key, {}) if proj_key else {}

        melee_types = []
        if melee.get("is_magical"):
            melee_types.append("magical")
        if (melee.get("ignition_amount") or 0) > 0:
            melee_types.append("flaming")

        missile_types = []
        if proj.get("is_magical"):
            missile_types.append("magical")
        if (proj.get("ignition_amount") or 0) > 0:
            missile_types.append("flaming")

        melee_dmg = (melee.get("damage") or 0) + (melee.get("ap_damage") or 0)
        missile_dmg = (proj.get("damage") or 0) + (proj.get("ap_damage") or 0) if proj else None

        run_speed = entity.get("run_speed")
        size = entity.get("size", "small")
        is_large = size in ("large", "very_large", "massive", "ultra")

        result[key] = {
            "bonus_hit_points": r["bonus_hit_points"],
            "armour": armour_val,
            "melee_attack": r["melee_attack"],
            "melee_defence": r["melee_defence"],
            "morale": r["morale"],
            "charge_bonus": r["charge_bonus"],
            "primary_ammo": r.get("primary_ammo") or 0,
            "melee_attack_types": melee_types,
            "weapon_damage": melee.get("damage"),
            "weapon_ap_damage": melee.get("ap_damage"),
            "weapon_strength": melee_dmg if melee else None,
            "run_speed": run_speed,
            "is_large": is_large,
            # missile
            "missile_range": proj.get("effective_range") if proj else None,
            "missile_damage": missile_dmg,
            "missile_base_damage": proj.get("damage") if proj else None,
            "missile_ap_damage": proj.get("ap_damage") if proj else None,
            "missile_damage_types": missile_types,
        }
    return result


def build_main_unit_map(rows):
    """unit key -> {land_unit, num_men, mp_cost, barrier, is_monstrous}"""
    return {r["unit"]: {
        "land_unit": r["land_unit"],
        "num_men": r["num_men"],
        "mp_cost": r["multiplayer_cost"],
        "barrier": int(r.get("barrier_health") or 0),
        "is_monstrous": r.get("is_monstrous", False),
    } for r in rows}


def build_special_ability_map(rows):
    """ability key -> gold_cost (round(additional_melee_cp + additional_missile_cp))"""
    return {r["key"]: round((r.get("additional_melee_cp") or 0) + (r.get("additional_missile_cp") or 0))
            for r in rows}


def build_agent_subtype_map(rows):
    """associated_unit_override (land_unit key) -> agent_subtype key.
    The associated_unit_override is the campaign unit that corresponds to this
    agent subtype. Multiple subtypes can share a unit; we keep the last-seen
    (order in table is stable across patches so this is deterministic).
    """
    result = {}
    for r in rows:
        unit_key = r.get("associated_unit_override") or ""
        if unit_key:
            result[unit_key] = r["key"]
    return result


def build_equipment_map(rows):
    """agent_subtype key -> [ancillary_key, ...] (non-mount ancillaries only).
    Mount ancillaries are recognised by '_anc_mount_' in their key and are
    excluded here since they are already captured in the 'mounts' field.
    """
    result = {}
    for r in rows:
        subtype = r["agent_subtype"]
        ancillary = r["ancillary"]
        if "_anc_mount_" not in ancillary:
            result.setdefault(subtype, []).append(ancillary)
    return result


def build_ancillary_cost_map(rows):
    """ancillary key -> gold_cost (uniqueness_score from ancillaries_tables).
    The uniqueness_score field stores the MP gold cost for each item.
    """
    return {r["key"]: r["uniqueness_score"] for r in rows if r.get("uniqueness_score") is not None}


def build_unit_ability_map(rows):
    """ability key -> {icon_name, type}"""
    return {r["key"]: {"icon_name": r["icon_name"], "type": r["type"]} for r in rows}


def build_ability_loc_maps(loc):
    """Returns (name_map, tooltip_map): ability_key -> display name / tooltip description."""
    name_prefix    = "unit_abilities_onscreen_name_"
    tooltip_prefix = "unit_abilities_tooltip_"
    names    = {}
    tooltips = {}
    for k, v in loc.items():
        if k and k.startswith(name_prefix):
            names[k[len(name_prefix):]] = v
        elif k and k.startswith(tooltip_prefix):
            tooltips[k[len(tooltip_prefix):]] = v
    return names, tooltips


# ---------------------------------------------------------------------------
# Ability icon copying
# ---------------------------------------------------------------------------

# Parses seed-abilities.sql to build: ability_key -> eid
ABILITY_SEED_EID_RE = re.compile(
    r"\(\d+,\s*'([0-9a-f\-]+)',\s*'([^']+)',"
)


def build_ability_key_eid_map(seed_filepath):
    """Returns {ability_key: eid} from the seed-abilities.sql INSERT rows."""
    result = {}
    with open(seed_filepath, encoding="utf-8") as f:
        for line in f:
            m = ABILITY_SEED_EID_RE.search(line)
            if m:
                result[m.group(2)] = m.group(1)
    return result


def copy_ability_icons(icons_dir, asset_dir, unit_ability_map, key_eid_map,
                       dry_run=False):
    """
    For each ability key in unit_ability_map, copies
      {icons_dir}/{icon_name}.png  →  {asset_dir}/{eid}.png
    then trims transparent borders with mogrify.
    """
    copied = 0
    missing_src = []
    missing_eid = []

    for key, info in unit_ability_map.items():
        icon_name = info.get("icon_name") or ""
        if not icon_name:
            continue

        eid = key_eid_map.get(key)
        if not eid:
            missing_eid.append(key)
            continue

        src = os.path.join(icons_dir, icon_name + ".png")
        if not os.path.isfile(src):
            missing_src.append(icon_name)
            continue

        dest = os.path.join(asset_dir, eid + ".png")
        if not dry_run:
            shutil.copy2(src, dest)
            subprocess.run(
                ["mogrify", "-fuzz", "20%", "-trim", "+repage", dest],
                check=True,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            )
        copied += 1

    print(f"  [icons] copied+trimmed {copied} icons", file=sys.stderr)
    if missing_src:
        print(f"  [icons] {len(missing_src)} source PNGs not found "
              f"(e.g. {missing_src[:3]})", file=sys.stderr)
    if missing_eid:
        print(f"  [icons] {len(missing_eid)} keys have no eid in seed "
              f"(e.g. {missing_eid[:3]})", file=sys.stderr)


# ---------------------------------------------------------------------------
# Name → unit key index via loc
# ---------------------------------------------------------------------------

def normalize_name(name):
    return (name
            .replace("\u2013", "-")
            .replace("\u2014", "-")
            .replace("\u2018", "'")
            .replace("\u2019", "'")
            .strip())


def build_name_index(land_units_loc, main_unit_map, land_unit_stats):
    """
    Returns: name -> [list of (unit_key, land_unit_key)] pairs.
    Parses onscreen_name entries from the land_units loc.
    """
    # land_units loc keys: "land_units_onscreen_name_<land_unit_key>" -> display name
    prefix = "land_units_onscreen_name_"
    lu_name_map = {}  # land_unit_key -> display_name
    for k, v in land_units_loc.items():
        if k.startswith(prefix):
            lu_key = k[len(prefix):]
            lu_name_map[lu_key] = normalize_name(v)

    # Build: name -> [(unit_key, land_unit_key), ...]
    # Go through main_units, look up land_unit display name
    index = {}
    for unit_key, mu in main_unit_map.items():
        lu_key = mu["land_unit"]
        name = lu_name_map.get(lu_key)
        if not name:
            continue
        index.setdefault(name, []).append((unit_key, lu_key))
    return index


def find_unit_key(unit_name, faction_prefixes, name_index, main_unit_map):
    """
    Find the best matching unit_key for a given display name + faction.
    Returns (unit_key, land_unit_key) or (None, None).
    """
    norm = normalize_name(unit_name)
    candidates = name_index.get(norm, [])
    if not candidates:
        return None, None

    if len(candidates) == 1:
        return candidates[0]

    # Multiple candidates: filter by faction prefix
    filtered = []
    for (unit_key, lu_key) in candidates:
        for prefix in faction_prefixes:
            if f"_{prefix}_" in unit_key or f"_{prefix}_" in lu_key:
                filtered.append((unit_key, lu_key))
                break

    if len(filtered) == 1:
        return filtered[0]
    if filtered:
        # Prefer latest game (wh3 > wh2 > wh)
        for game_prefix in ("wh3_", "wh2_", "wh_"):
            for item in filtered:
                if item[0].startswith(game_prefix):
                    return item
        return filtered[0]

    # Fallback: no faction match, return first candidate
    return candidates[0]


# ---------------------------------------------------------------------------
# Stats extraction
# ---------------------------------------------------------------------------

def extract_stats(unit_key, main_unit_map, land_unit_stats,
                  agent_subtype_map=None, equipment_map=None, ancillary_cost_map=None):
    mu = main_unit_map.get(unit_key)
    if not mu:
        return None
    land_unit_key = mu["land_unit"]
    lu = land_unit_stats.get(land_unit_key)
    if not lu:
        return None

    speed = lu.get("run_speed")
    if speed is not None:
        speed = round(speed)

    stats = {
        "cost": mu["mp_cost"],
        "is_large": lu["is_large"] or mu.get("is_monstrous", False),
        "unit_size": mu["num_men"],
        "health": lu["bonus_hit_points"],
        "barrier": mu["barrier"] or 0,
        "armor": lu["armour"],
        "leadership": lu["morale"],
        "speed": speed,
        "melee_attack": lu["melee_attack"],
        "melee_attack_types": lu["melee_attack_types"],
        "melee_defence": lu["melee_defence"],
        "weapon_strength": lu["weapon_strength"],
        "weapon_damage": lu["weapon_damage"],
        "weapon_ap_damage": lu["weapon_ap_damage"],
        "charge_bonus": lu["charge_bonus"],
        "ammunition": lu["primary_ammo"],
        "missile_damage_types": lu["missile_damage_types"],
    }

    if lu.get("missile_range"):
        stats["range"] = lu["missile_range"]
    if lu.get("missile_damage") is not None:
        stats["missile_damage"] = lu["missile_damage"]
    if lu.get("missile_base_damage") is not None:
        stats["missile_base_damage"] = lu["missile_base_damage"]
    if lu.get("missile_ap_damage") is not None:
        stats["missile_ap_damage"] = lu["missile_ap_damage"]

    # Equipment: unique items for this character from ancillaries_included_agent_subtypes.
    # Only populated for legendary lords/heroes that have character-specific items.
    if agent_subtype_map and equipment_map:
        agent_subtype = agent_subtype_map.get(land_unit_key)
        if agent_subtype:
            items = equipment_map.get(agent_subtype)
            if items:
                stats["equipment"] = [
                    {"key": k, "gold_cost": (ancillary_cost_map or {}).get(k)}
                    for k in items
                ]

    return {k: v for k, v in stats.items() if v is not None}


# ---------------------------------------------------------------------------
# Seed file updating
# ---------------------------------------------------------------------------

STATS_BLOCK_RE = re.compile(
    r"(\(\d+,\s*'[0-9a-f\-]+',\s*'((?:[^']|'')*)',\s*'(?:[^']|'')*',\s*\n\s*\d+,\s*\d+,\s*\d+,\s*\d+,\s*')(\{[^']*\})(')"
)


def update_unit_seed_file(filepath, faction_name, faction_prefixes, name_index,
                          main_unit_map, land_unit_stats,
                          agent_subtype_map=None, equipment_map=None, ancillary_cost_map=None):
    with open(filepath, encoding="utf-8") as f:
        content = f.read()

    not_found = []
    found = 0
    no_game_data = []

    def replacer(m):
        nonlocal found
        prefix_str = m.group(1)
        raw_name = m.group(2).replace("''", "'")
        old_stats_str = m.group(3)
        suffix = m.group(4)

        unit_key, lu_key = find_unit_key(raw_name, faction_prefixes, name_index, main_unit_map)
        if unit_key is None:
            not_found.append(raw_name)
            return m.group(0)

        new_stats = extract_stats(unit_key, main_unit_map, land_unit_stats,
                                  agent_subtype_map, equipment_map, ancillary_cost_map)
        if new_stats is None:
            no_game_data.append(raw_name)
            return m.group(0)

        # Preserve non-stat fields from existing stats (abilities, mounts, draftable-spells,
        # equipment) and append them in canonical order at the end. Equipment from RPFM
        # takes precedence over any previously stored value.
        try:
            old_stats = json.loads(old_stats_str)
        except Exception:
            old_stats = {}

        for preserve_key in ("abilities", "draftable-spells", "mounts"):
            if preserve_key in old_stats:
                new_stats[preserve_key] = old_stats[preserve_key]
        # equipment is sourced from game data — only fall back to old value if RPFM
        # returned nothing (unit not in ancillaries_included_agent_subtypes).
        if "equipment" not in new_stats and "equipment" in old_stats:
            new_stats["equipment"] = old_stats["equipment"]

        found += 1
        return prefix_str + json.dumps(new_stats, separators=(",", ":")) + suffix

    new_content = STATS_BLOCK_RE.sub(replacer, content)

    if not_found:
        print(f"  [{faction_name}] {len(not_found)} units not matched in loc: "
              f"{not_found[:5]}{'...' if len(not_found) > 5 else ''}", file=sys.stderr)
    if no_game_data:
        print(f"  [{faction_name}] {len(no_game_data)} units matched but no game data: "
              f"{no_game_data[:5]}", file=sys.stderr)
    print(f"  [{faction_name}] updated {found} units", file=sys.stderr)
    return new_content


# ---------------------------------------------------------------------------
# Ability seed updating
# ---------------------------------------------------------------------------

# Matches one INSERT row in seed-abilities.sql:
# (id, 'eid', 'key', 'name', 'description', 'ability_type', ...)
# Captures: full_match, id, eid, key, name, description, ability_type, rest_of_row
ABILITY_ROW_RE = re.compile(
    r"(\(\d+,\s*'([^']+)',\s*'([^']+)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'([^']+)'(,\s*[^)]+))",
    re.DOTALL,
)

# Same pattern but with icon column already present — 7th string column is icon.
ABILITY_ROW_WITH_ICON_RE = re.compile(
    r"(\(\d+,\s*'([^']+)',\s*'([^']+)',\s*'((?:[^']|'')*)',\s*'((?:[^']|'')*)',\s*'([^']+)',\s*'([^']*)'(,\s*[^)]+))",
    re.DOTALL,
)


def _sql_escape(s):
    return (s or "").replace("'", "''")


def update_ability_seed_file(filepath, ability_name_map, ability_tooltip_map):
    """
    Rewrites seed-abilities.sql refreshing name and description from game loc.
    Icon is NOT stored in the DB — it is derived from the eid at render time.
    """
    with open(filepath, encoding="utf-8") as f:
        content = f.read()

    # Strip icon column from INSERT header if present (idempotent)
    content = content.replace(
        "INSERT OR IGNORE INTO ability(id, eid, key, name, description, ability_type, icon,",
        "INSERT OR IGNORE INTO ability(id, eid, key, name, description, ability_type,",
    ).replace(
        "INSERT OR REPLACE INTO ability(id, eid, key, name, description, ability_type, icon,",
        "INSERT OR REPLACE INTO ability(id, eid, key, name, description, ability_type,",
    )

    updated   = 0
    not_found = 0

    def refresh_row(m):
        nonlocal updated, not_found
        full         = m.group(0)
        eid          = m.group(2)
        key          = m.group(3)
        _name        = m.group(4)
        _desc        = m.group(5)
        ability_type = m.group(6)
        rest         = m.group(7)
        id_part      = full.lstrip("(").split(",")[0].strip()
        name = ability_name_map.get(key)
        desc = ability_tooltip_map.get(key)
        if name is None and desc is None:
            not_found += 1
            return full
        name = name or _name.replace("''", "'")
        desc = desc or _desc.replace("''", "'")
        updated += 1
        return (f"({id_part}, '{eid}', '{key}', '{_sql_escape(name)}', "
                f"'{_sql_escape(desc)}', '{ability_type}'{rest}")

    # Handle rows that may still have an icon column value (7 string cols)
    def strip_icon_and_refresh(m):
        nonlocal updated, not_found
        full         = m.group(0)
        eid          = m.group(2)
        key          = m.group(3)
        _name        = m.group(4)
        _desc        = m.group(5)
        ability_type = m.group(6)
        rest         = m.group(8)   # skip icon group (7)
        id_part      = full.lstrip("(").split(",")[0].strip()
        name = ability_name_map.get(key) or _name.replace("''", "'")
        desc = ability_tooltip_map.get(key) or _desc.replace("''", "'")
        updated += 1
        return (f"({id_part}, '{eid}', '{key}', '{_sql_escape(name)}', "
                f"'{_sql_escape(desc)}', '{ability_type}'{rest}")

    if ABILITY_ROW_WITH_ICON_RE.search(content):
        new_content = ABILITY_ROW_WITH_ICON_RE.sub(strip_icon_and_refresh, content)
    else:
        new_content = ABILITY_ROW_RE.sub(refresh_row, content)

    print(f"  [abilities] updated {updated} rows, {not_found} without loc entry",
          file=sys.stderr)
    return new_content


# ---------------------------------------------------------------------------
# Spell seed updating
# ---------------------------------------------------------------------------

# Regex for when gold_cost column already exists in the INSERT:
# Matches: ..., spell_type, mana_cost, CURRENT_gold_cost, game_id, version, ...
# Groups: (prefix_through_mana_cost, spell_key, current_gold_cost, post_gold_cost_suffix)
SPELL_UPDATE_RE = re.compile(
    r"(\(\d+,\s*'[0-9a-f\-]+',\s*'([^']+)',\s*'(?:[^']|'')*',\s*'(?:[^']|'')*',\s*'[^']+',\s*\d+,\s*)(\d+)(,\s*\d+,\s*\d+,\s*')"
)

# Regex for when gold_cost column does NOT exist yet:
# Matches: ..., spell_type, mana_cost, game_id, version, ...
# Groups: (prefix_through_spell_type, spell_key, mana_cost, post_mana_cost_suffix)
SPELL_INSERT_RE = re.compile(
    r"(\(\d+,\s*'[0-9a-f\-]+',\s*'([^']+)',\s*'(?:[^']|'')*',\s*'(?:[^']|'')*',\s*'[^']+',\s*)(\d+)(,\s*\d+,\s*\d+,\s*')"
)


def update_spell_seed_file(filepath, special_ability_map):
    with open(filepath, encoding="utf-8") as f:
        content = f.read()

    not_found = []
    found = 0
    has_gold_cost = "gold_cost" in content

    if has_gold_cost:
        pattern = SPELL_UPDATE_RE
    else:
        print("  seed-spells.sql does not have gold_cost column — skipping spell update", file=sys.stderr)
        return content

    def replacer(m):
        nonlocal found
        prefix_str = m.group(1)  # everything up to and including mana_cost (with gold_cost mode: up to mana_cost)
        spell_key = m.group(2)
        _old_gold = m.group(3)  # existing gold_cost value (to be replaced)
        suffix = m.group(4)     # ", game_id, version, 'uuid'..."

        gold_cost = special_ability_map.get(spell_key)
        if gold_cost is None:
            not_found.append(spell_key)
            gold_cost = 0
        else:
            found += 1

        return prefix_str + str(gold_cost) + suffix

    new_content = pattern.sub(replacer, content)
    print(f"  Updated {found} spell gold costs, {len(not_found)} not found", file=sys.stderr)
    return new_content


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Update seed data from RPFM-decoded game tables.")
    parser.add_argument("--data-dir", required=True,
                        help="Directory containing RPFM-decoded JSON table files.")
    parser.add_argument("--icons-dir",
                        help="Directory containing extracted ability icon PNGs "
                             "(named by icon_name from unit_abilities_tables). "
                             "If omitted, icon copying is skipped.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Print what would change without writing files.")
    args = parser.parse_args()

    d = args.data_dir

    def path(name):
        return os.path.join(d, name)

    print("Loading game tables...", file=sys.stderr)

    _, armour_rows = parse_rpfm_table(path("unit_armour_types_tables.json"))
    armour_map = build_armour_map(armour_rows)
    print(f"  armour types: {len(armour_map)}", file=sys.stderr)

    _, entity_rows = parse_rpfm_table(path("battle_entities_tables.json"))
    entity_map = build_entity_map(entity_rows)
    print(f"  battle entities: {len(entity_map)}", file=sys.stderr)

    _, melee_rows = parse_rpfm_table(path("melee_weapons_tables.json"))
    melee_map = build_melee_weapon_map(melee_rows)
    print(f"  melee weapons: {len(melee_map)}", file=sys.stderr)

    _, mwep_rows = parse_rpfm_table(path("missile_weapons_tables.json"))
    missile_wep_map = build_missile_weapon_map(mwep_rows)
    print(f"  missile weapons: {len(missile_wep_map)}", file=sys.stderr)

    _, proj_rows = parse_rpfm_table(path("projectiles_tables.json"))
    projectile_map = build_projectile_map(proj_rows)
    print(f"  projectiles: {len(projectile_map)}", file=sys.stderr)

    _, lu_rows = parse_rpfm_table(path("land_units_tables.json"))
    land_unit_stats = build_land_unit_map(lu_rows, armour_map, entity_map, melee_map,
                                          missile_wep_map, projectile_map)
    print(f"  land units: {len(land_unit_stats)}", file=sys.stderr)

    _, mu_rows = parse_rpfm_table(path("main_units_tables.json"))
    main_unit_map = build_main_unit_map(mu_rows)
    print(f"  main units: {len(main_unit_map)}", file=sys.stderr)

    _, sa_rows = parse_rpfm_table(path("unit_special_abilities_tables.json"))
    special_ability_map = build_special_ability_map(sa_rows)
    print(f"  special abilities: {len(special_ability_map)}", file=sys.stderr)

    land_units_loc = parse_loc_file(path("land_units_loc.json"))
    print(f"  land units loc: {len(land_units_loc)} entries", file=sys.stderr)

    _, agent_subtype_rows = parse_rpfm_table(path("agent_subtypes_tables.json"))
    agent_subtype_map = build_agent_subtype_map(agent_subtype_rows)
    print(f"  agent subtypes: {len(agent_subtype_map)}", file=sys.stderr)

    _, anc_subtype_rows = parse_rpfm_table(path("ancillaries_included_agent_subtypes_tables.json"))
    equipment_map = build_equipment_map(anc_subtype_rows)
    print(f"  equipment (agent subtypes with items): {len(equipment_map)}", file=sys.stderr)

    _, ancillaries_rows = parse_rpfm_table(path("ancillaries_tables.json"))
    ancillary_cost_map = build_ancillary_cost_map(ancillaries_rows)
    print(f"  ancillary gold costs: {len(ancillary_cost_map)}", file=sys.stderr)

    _, ua_rows = parse_rpfm_table(path("unit_abilities_tables.json"))
    unit_ability_map = build_unit_ability_map(ua_rows)
    print(f"  unit abilities (icons): {len(unit_ability_map)}", file=sys.stderr)

    ua_loc = parse_loc_file(path("unit_abilities_loc.json"))
    ability_name_map, ability_tooltip_map = build_ability_loc_maps(ua_loc)
    print(f"  ability loc: {len(ability_name_map)} names, {len(ability_tooltip_map)} tooltips",
          file=sys.stderr)

    print("Building name index...", file=sys.stderr)
    name_index = build_name_index(land_units_loc, main_unit_map, land_unit_stats)
    print(f"  {len(name_index)} unique unit names indexed", file=sys.stderr)

    print("Updating unit seed files...", file=sys.stderr)
    for faction_name, faction_prefixes in FACTION_KEY_MAP.items():
        filename = f"seed-{faction_name}-units.sql"
        filepath = os.path.join(SEED_DIR, filename)
        if not os.path.exists(filepath):
            print(f"  SKIP (not found): {filename}", file=sys.stderr)
            continue

        new_content = update_unit_seed_file(
            filepath, faction_name, faction_prefixes,
            name_index, main_unit_map, land_unit_stats,
            agent_subtype_map, equipment_map, ancillary_cost_map
        )
        if not args.dry_run:
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(new_content)

    print("Updating ability descriptions...", file=sys.stderr)
    ability_file = os.path.join(SEED_DIR, "seed-abilities.sql")
    new_abilities = update_ability_seed_file(ability_file, ability_name_map, ability_tooltip_map)
    if not args.dry_run:
        with open(ability_file, "w", encoding="utf-8") as f:
            f.write(new_abilities)

    if args.icons_dir:
        print("Copying and trimming ability icons...", file=sys.stderr)
        ability_seed_file = os.path.join(SEED_DIR, "seed-abilities.sql")
        key_eid_map = build_ability_key_eid_map(ability_seed_file)
        asset_dir = os.path.join("bases", "rts-api", "resources", "rts-api",
                                 "asset", "icon", "ability")
        copy_ability_icons(args.icons_dir, asset_dir, unit_ability_map,
                           key_eid_map, dry_run=args.dry_run)
    else:
        print("  [icons] --icons-dir not provided, skipping icon copy", file=sys.stderr)

    print("Updating spell gold costs...", file=sys.stderr)
    spell_file = os.path.join(SEED_DIR, "seed-spells.sql")
    new_spell = update_spell_seed_file(spell_file, special_ability_map)
    if not args.dry_run:
        with open(spell_file, "w", encoding="utf-8") as f:
            f.write(new_spell)

    print("Done.", file=sys.stderr)


if __name__ == "__main__":
    main()
