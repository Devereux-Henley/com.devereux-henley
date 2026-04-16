# rpfm-scraper

A CLI tool that reads RPFM-decoded game tables from Total War: Warhammer III and regenerates seed SQL files for the database. Used after each game patch to refresh unit statistics, spell costs, item pools, and mount data.

## Sub-documents

- [Game Data](rpfm-scraper/game-data.md) — full refresh workflow, what gets updated, RPFM table paths, known limitations
- [Draft Rules — RPFM Cap Data Handoff](rpfm-scraper/draft-rules-rpfm-handoff.md) — task brief for scraping per-unit and categorical cap data

## Key paths

| Path | Purpose |
|------|---------|
| `bases/rpfm-scraper/src/.../core.clj` | CLI entry point |
| `bases/rpfm-scraper/data/` | RPFM-decoded JSON files (gitignored) |
| `bases/rpfm-scraper/assets/` | Extracted game icons and unit cards (gitignored) |
| `components/rts-data/resources/rts-data/sql/seed/` | Generated seed SQL files |

## Usage

```bash
# From repo root
clojure -M:dev -m com.devereux-henley.rpfm-scraper.core --data-dir bases/rpfm-scraper/data

# Or via uber JAR
clojure -M:build -A rpfm-scraper uber
java -jar target/rpfm-scraper.jar --data-dir bases/rpfm-scraper/data
```

Use `--dry-run` to preview changes without writing files. See [game-data.md](rpfm-scraper/game-data.md) for the full workflow including icon extraction.
