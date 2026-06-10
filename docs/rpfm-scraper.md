# rpfm-scraper

A CLI tool that reads curated authoring EDN plus RPFM-decoded game tables from Total War: Warhammer III and writes the merged per-patch Datalog EDN seed. Used after each game patch to refresh unit statistics, spell costs, item pools, and mount data.

## Sub-documents

- [Game Data](rpfm-scraper/game-data.md) — full refresh workflow, what gets updated, RPFM table paths, known limitations
- [EDN Seed Pipeline](rpfm-scraper/edn-seed-pipeline.md) — how curated authoring EDN merges with RPFM output into the committed seed
- [Draft Rules — RPFM Cap Data Handoff](rpfm-scraper/draft-rules-rpfm-handoff.md) — task brief for scraping per-unit and categorical cap data

## Key paths

| Path | Purpose |
|------|---------|
| `bases/rpfm-scraper/src/.../core.clj` | CLI entry point |
| `bases/rpfm-scraper/data/` | RPFM-decoded JSON files (gitignored) |
| `bases/rpfm-scraper/assets/` | Extracted game icons and unit cards (gitignored) |
| `components/rts-data/resources/rts-data/seed/authoring/<patch>/` | Curated authoring EDN (identities, hand-maintained fields) |
| `components/rts-data/resources/rts-data/seed/datalog/<patch>/` | Generated merged Datalog seed (committed, loaded by the API) |

## Usage

```bash
# From repo root
clojure -M:dev -m com.devereux-henley.rpfm-scraper.core \
  --data-dir bases/rpfm-scraper/data --patch-version 8.0

# Or via uber JAR
clojure -M:build -A rpfm-scraper uber
java -jar target/rpfm-scraper.jar --data-dir bases/rpfm-scraper/data --patch-version 8.0
```

Use `--dry-run` to preview changes without writing files. Pass `--strict` to fail (exit non-zero) when any unit row lacks a `key`, lacks an `<eid>.png` in `asset/card/unit/`, or any PNG in that directory no longer corresponds to a unit row — required for the periodic data-refresh job so coverage gaps surface as a red build instead of a warning line. Every run (strict or not) writes `target/scraper-coverage.json` with the missing-key, missing-icon, and stale-PNG names so a CI run has a single artefact to diff between scrapes.

See [game-data.md](rpfm-scraper/game-data.md) for the full workflow including icon extraction.
