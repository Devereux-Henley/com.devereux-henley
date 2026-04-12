#!/usr/bin/env python3
"""
Fix the two cases that migrate_is_unique.py couldn't handle:

1. Multi-line rows whose JSON contains SQL-escaped single quotes ('' inside the blob).
   These rows were skipped by the first pass and are now missing their is_unique value,
   making the column mapping wrong: version→is_unique, sub→version.

2. Compact-format rows where all non-id columns are on 2–3 dense lines instead of
   one value per line.  The first pass didn't recognise the single-line
   "version, 'f0ce...' " pattern.

Detection:
  Multi-line non-transformed:  JSON line → bare number line → 'f0ce...' line
  Compact non-transformed:     JSON line → "N, 'f0ce..." line

Already-transformed multi-line rows have TWO numeric lines before 'f0ce...',
so the check "next-next line starts with 'f0ce'" safely distinguishes them.
"""
import json
import re
from pathlib import Path

SEED_DIR = Path("components/rts-data/resources/rts-data/sql/seed")

# Matches a line whose last value is the JSON blob (followed by comma).
# prefix captures anything before the opening quote (handles compact rows like
# "   1, 9, 11, 5, '{...}',").
JSON_LINE_RE = re.compile(r"^(?P<prefix>.*?)'(?P<json>\{(?:[^']|'')*\})',\s*$")

# Matches a line that is only a bare integer (version or is_unique)
INT_ONLY_RE = re.compile(r"^\s*\d+,\s*$")

# Matches the created_by_sub line
SUB_LINE_RE = re.compile(r"^\s*'f0ce7395-a57f-41e9-ade0-fd13bafc058f'")

# Matches a compact line:  "N, 'f0ce..." — version and sub on the same line
COMPACT_SUB_RE = re.compile(r"^(?P<indent>\s*)(?P<version>\d+), '(?P<rest>f0ce7395[^']*',.*)")


def compute_is_unique(obj: dict) -> tuple[int, dict]:
    if "is_unique" in obj:
        return (1 if obj.pop("is_unique") else 0), obj
    return (1 if obj.get("equipment") else 0), obj


def transform_json(raw_sql: str) -> tuple[int, str]:
    """Parse SQL-string JSON (unescaping ''), compute is_unique, return (val, new_sql_str)."""
    unescaped = raw_sql.replace("''", "'")
    obj = json.loads(unescaped)
    is_unique_val, obj = compute_is_unique(obj)
    new_json = json.dumps(obj, separators=(",", ":"))
    new_sql = new_json.replace("'", "''")
    return is_unique_val, new_sql


def fix_file(path: Path) -> int:
    lines = path.read_text().splitlines(keepends=True)
    result: list[str] = []
    fixes = 0
    i = 0

    while i < len(lines):
        line = lines[i]
        m = JSON_LINE_RE.match(line)

        if m:
            prefix = m.group("prefix")
            # Derive indent from the prefix (whitespace before any values)
            indent = re.match(r"^\s*", prefix).group(0)
            json_raw = m.group("json")
            ahead1 = lines[i + 1] if i + 1 < len(lines) else ""
            ahead2 = lines[i + 2] if i + 2 < len(lines) else ""

            # ── Case 1: multi-line, not yet transformed ──────────────────────
            # Next line: bare integer (version)
            # Line after that: 'f0ce...' (sub)
            if INT_ONLY_RE.match(ahead1) and SUB_LINE_RE.match(ahead2):
                is_unique_val, new_sql = transform_json(json_raw)
                result.append(f"{prefix}'{new_sql}',\n")
                result.append(f"{indent}{is_unique_val},\n")
                result.append(ahead1)   # original version line
                result.append(ahead2)   # original sub line
                fixes += 1
                i += 3
                continue

            # ── Case 2: compact format ────────────────────────────────────────
            # Next line starts with: "N, 'f0ce..."
            cm = COMPACT_SUB_RE.match(ahead1)
            if cm:
                is_unique_val, new_sql = transform_json(json_raw)
                # Reconstruct the JSON line (preserving any preceding values)
                result.append(f"{prefix}'{new_sql}',\n")
                # Prepend is_unique before version on the same compact line
                new_ahead = (
                    f"{cm.group('indent')}{is_unique_val}, "
                    f"{cm.group('version')}, '{cm.group('rest')}\n"
                )
                result.append(new_ahead)
                fixes += 1
                i += 2
                continue

        result.append(line)
        i += 1

    if fixes:
        path.write_text("".join(result))

    return fixes


def main() -> None:
    total = 0
    for path in sorted(SEED_DIR.glob("seed-*-units.sql")):
        n = fix_file(path)
        if n:
            print(f"fixed {n:3d} rows  {path.name}")
        total += n
    print(f"\n{total} rows fixed across all files.")


if __name__ == "__main__":
    main()
