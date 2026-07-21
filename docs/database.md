# Database

## Overview

Persistent state lives in a [Datalevin](https://github.com/juji-io/datalevin) store — an embedded, LMDB-backed Datalog database. There is no SQL layer and no migration framework: the Datalog schema merges into the store when the connection opens, and game data arrives as committed EDN seed files.

The database layer is split across three Polylith units:

| Unit | Role |
|---|---|
| `components/datalog` | Thin wrapper over `datalevin.core`: connection lifecycle (`get-conn`, `close`), reads (`q`, `db`, `pull`, `entity`, `lookup-ref`), writes (`transact!`), and `update-schema`. The seam through which all domain and web code touches Datalevin — apart from the rts-api base's connection key, no other namespace requires `datalevin.core`. |
| `components/rts-data-access` | Per-domain pull patterns and query/mutation functions under `query/datalog/<domain>.clj`, plus the attribute schema under `schema/datalog/<domain>.clj`, merged into the full `datalog-schema` exposed by the contract. |
| `components/rts-data` | Per-patch EDN seed files under `resources/rts-data/seed/datalog/<patch-version>/` and the loaders that read them into tx-data. Deliberately has no Datalevin dependency — callers pass the tx-data to `datalog.contract/transact!` themselves. |

The `rts-api` base owns the Integrant connection key (`::datalog/connection` in `bases/rts-api/src/.../datalog.clj`). On `init-key` it opens the store at `db/datalevin/` (override with `DATALEVIN_DB_DIR`) with the full data-access schema; handlers receive the connection as `:datalog-connection` in their dependency map.

---

## Schema

The attribute schema lives in `components/rts-data-access/src/.../schema/datalog/`, one namespace per domain, merged in `schema/datalog.clj`. `datalevin/get-conn` merges the schema into any pre-existing store, so **additive** changes (new attributes, new entity types) apply on the next restart with no migration step.

**Non-additive** changes (renaming or retracting an attribute, changing a value type) have no upgrade path — wipe the store and rebuild. The project is not deployed, so there is no production data to preserve; a fresh store seeded from EDN is always the target state. From the REPL, `(claude-workspace/reset-datalog!)` halts the system, deletes the LMDB directory, and restarts against an empty store.

---

## Seed data

Game data (units, factions, abilities, items, mounts, statlines, …) is committed as per-patch EDN under `components/rts-data/resources/rts-data/seed/datalog/<patch-version>/`, one file per entity type. The files are produced by the `rpfm-scraper` base, which merges curated authoring EDN (`seed/authoring/<patch-version>/`) with RPFM-decoded game tables — see [docs/rpfm-scraper/edn-seed-pipeline.md](rpfm-scraper/edn-seed-pipeline.md).

`rts-data.contract/load-datalog-seed` returns `[file-name tx-data]` pairs in dependency order (independent entities first, junction rows and per-patch statlines last, so lookup-ref targets always exist). Seeding is not automatic: run `(claude-workspace/seed-datalog!)` from the REPL after `(go!)`. Every seeded entity carries a `:db.unique/identity` attribute, so re-running the seed upserts in place.

For a store populated with demo tournaments (brackets in interesting states, not just game data), run the `rts-demo` base instead — it wipes and rebuilds the store from scratch.

---

## Conventions

- **Snapshot once per read.** Call `datalog.contract/db` once at the top of a read function and run every query in that function against the snapshot, so multi-step reads see a consistent view.
- **Flatten pull results.** Read functions in `rts-data-access` flatten pull maps into the flat `*-eid` shape handlers consume (ref sub-maps become `:game-eid`, `:faction-eid`, …); handlers never see raw pull structure.
- **`:db.type/instant` requires `java.util.Date`.** Datalevin rejects `java.time.Instant` for instant attributes. Coerce `Instant` → `Date` at the data-access boundary in every mutation function.
- **`:db.type/idoc` for document values.** Store nested Clojure maps directly under a document-typed attribute (e.g. `:replay/parsed-data`); Datalevin round-trips them as maps across store reopen.
