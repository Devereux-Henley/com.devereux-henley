#!/usr/bin/env python3
"""
Migrate is_unique from unit_statistics JSON blob to dedicated unit table column.

For each unit seed file:
  - Adds is_unique to the INSERT column list (after unit_statistics)
  - Extracts is_unique from the JSON if present (true→1, false→0)
  - Falls back to 1 if the unit has non-empty equipment, else 0
  - Removes is_unique from the JSON
"""
import json
import re
from pathlib import Path

SEED_DIR = Path("components/rts-data/resources/rts-data/sql/seed")

OLD_COLS = ("unit_type_id, unit_category_id, unit_statistics,\n"
            "                            version,")
NEW_COLS = ("unit_type_id, unit_category_id, unit_statistics,\n"
            "                            is_unique, version,")

# Matches the JSON literal (no single quotes inside) followed by the version line.
# Group 1: the raw JSON string
# Group 2: leading whitespace on the version line
# Group 3: version number + rest up to and including the created_by_sub UUID
PATTERN = re.compile(
    r"'(\{[^']+\})',\n"
    r"(\s+)"
    r"(\d+,\n\s+'f0ce7395-a57f-41e9-ade0-fd13bafc058f')"
)


def compute_is_unique(obj: dict) -> tuple[int, dict]:
    """Return (is_unique_int, obj_without_is_unique)."""
    if "is_unique" in obj:
        value = 1 if obj.pop("is_unique") else 0
    else:
        value = 1 if obj.get("equipment") else 0
    return value, obj


def replace_json(m: re.Match) -> str:
    json_str = m.group(1)
    indent = m.group(2)
    version_line = m.group(3)

    try:
        obj = json.loads(json_str)
    except json.JSONDecodeError as exc:
        raise ValueError(f"JSON parse error: {exc}\nInput: {json_str[:120]}") from exc

    is_unique_val, obj = compute_is_unique(obj)
    new_json = json.dumps(obj, separators=(",", ":"))
    return (
        f"'{new_json}',\n"
        f"{indent}{is_unique_val},\n"
        f"{indent}{version_line}"
    )


def transform_file(path: Path) -> bool:
    text = path.read_text()

    if "INSERT OR REPLACE INTO unit(" not in text:
        return False

    if OLD_COLS not in text:
        print(f"  WARNING: column pattern not found in {path.name} — skipping")
        return False

    text = text.replace(OLD_COLS, NEW_COLS)
    text = PATTERN.sub(replace_json, text)
    path.write_text(text)
    return True


def main() -> None:
    for path in sorted(SEED_DIR.glob("seed-*-units.sql")):
        changed = transform_file(path)
        print(f"{'updated' if changed else 'skipped':8s}  {path.name}")


if __name__ == "__main__":
    main()
