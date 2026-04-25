# Handoff: Auto-emit `seed-unit-keys.sql` from rpfm-scraper

## Context

The post-match modal (PR #35) records a tournament match by parsing one
`.replay` file per game and joining each parsed unit's engine
`land_units` key (e.g. `wh_main_emp_inf_halberdiers`) back to a `unit`
row so it can render the display name, points cost, and category. The
join column is `unit.key` (added in migration `000006-create-unit-table`
on the redesign branch — re-applied to existing migrations because we
aren't deployed yet).

That column is populated by a single seed file,
`components/rts-data/resources/rts-data/sql/seed/seed-unit-keys.sql`,
which is currently **hand-curated** to cover only the units that appear
in the bundled e2e replay fixture (Empire + Chaos Dwarfs). Every other
faction's units have `key = NULL` and therefore render in the modal as
raw engine keys with no point totals.

The fix is to teach the scraper to emit this seed file from the data it
already has, so a normal scraper refresh gives every unit a key. Once
that's done the hand-curated block can be deleted.

---

## What the scraper already has

`bases/rpfm-scraper/src/com/devereux_henley/rpfm_scraper/name_match.clj`
builds `display-name → [[unit-key land-unit-key] ...]` from the RPFM
`main_units_tables` and `land_units_onscreen_name_*` loc strings. The
`unit-key` here **is** what we need (`wh_main_emp_inf_halberdiers` and
friends). It's the `unit` column of `main_units_tables`.

`update-unit-seed-file` (in `stats.clj`) already iterates every INSERT
row in each `seed-<faction>-units.sql`, looks up the unit-key for that
display name via `nm/find-unit-key`, and uses it to fetch fresh stats.
The unit-key currently flows through that function and is then dropped
on the floor — we just don't persist it.

---

## What to build

### 1. Add a `key` field to the unit-resolution result

`nm/find-unit-key` already returns `[unit-key land-unit-key]`. No change
needed. `stats/update-unit-seed-file` already destructures it:

```clojure
[unit-key _lu] (nm/find-unit-key raw-name faction-prefixes name-index)
```

It throws away `unit-key` after looking up stats. We need to surface it.

### 2. Capture per-faction `eid → unit-key` mappings during the stats pass

The cleanest place to gather them is in `stats/update-unit-seed-file`'s
`replacer` closure: alongside `not-found`/`no-data`/`found` atoms, add a
`pairs` atom that collects `[eid unit-key]` tuples (the row's `eid` is
already in the matched regex group — see `stats-block-re`; you'll need
to widen the regex so `eid` is captured, currently only `name` is).

Return value: keep returning the rewritten file content as today, but
expose the pairs by either (a) returning a map `{:content … :pairs …}`
and updating `update-unit-seeds!` in `core.clj` accordingly, or
(b) writing them to a `*pairs-out*` dynamic var the caller can rebind.
Option (a) is cleaner.

### 3. Emit `seed-unit-keys.sql` after the stats pass

After `update-unit-seeds!` finishes (top of `core.clj`,
~line 178), aggregate the per-faction `:pairs` lists and spit one file:

```
components/rts-data/resources/rts-data/sql/seed/seed-unit-keys.sql
```

Format must be a **single SQL statement** (the seed runner is
`next.jdbc.execute!`, which only runs the first statement of a
multi-statement string — see how the current hand-curated file is a
single CASE-based UPDATE).

```sql
UPDATE unit
SET key = CASE eid
  WHEN '<eid-1>' THEN '<unit-key-1>'
  WHEN '<eid-2>' THEN '<unit-key-2>'
  …
  ELSE key
END;
```

A helper for this lives in spirit in `mounts_seed.clj` (look at how it
emits its INSERT — same generator pattern, different shape).

### 4. Delete the hand-curated entries

Once the scraper emits the file, the existing block in
`components/rts-data/resources/rts-data/sql/seed/seed-unit-keys.sql`
should be replaced by the scraper output. The seed file's location and
name are unchanged — `seed-unit-keys.sql` is already the last entry in
`seed-files` in `components/rts-data/src/com/devereux_henley/rts_data/contract.clj`.

The file's leading `TODO(scraper)` comment can be deleted once this work
lands.

### 5. Idempotency / preserve-on-empty

Match the convention from `update-unit-lores!` in `core.clj`
(~lines 274–287): if a scraper run finds no unit-key pairs (e.g. data
files missing), preserve the existing on-disk seed-unit-keys.sql rather
than overwriting it with an empty file. Otherwise a partial run wipes
the seed.

---

## How to verify

After running the scraper end-to-end:

1. The diff to `seed-unit-keys.sql` should show a single CASE-based
   UPDATE with a WHEN per unit row across **every** faction seed (not
   just Empire + Chaos Dwarfs).
2. `clojure -M:dev:claude` then `(claude-workspace/halt!)`,
   `rm db/database.db`, `(claude-workspace/go!)`,
   `(claude-workspace/seed-db!)` from a REPL.
3. `sqlite3 db/database.db "SELECT COUNT(key) FROM unit"` should equal
   the unit count (currently 1662 in fresh seed; check
   `SELECT COUNT(*) FROM unit`).
4. Drop a `.replay` file at the post-match modal and confirm the review
   step shows real unit names + costs for the OPPONENT's faction too,
   not just Empire/Chaos Dwarfs.

---

## Pointers

| What | Where |
|---|---|
| `unit-key` data source | `bases/rpfm-scraper/src/com/devereux_henley/rpfm_scraper/name_match.clj:39` (`find-unit-key`) |
| Per-row eid + name regex | `bases/rpfm-scraper/src/com/devereux_henley/rpfm_scraper/stats.clj:93` (`stats-block-re`) — widen capture group to also expose the eid |
| Stats-rewrite caller | `bases/rpfm-scraper/src/com/devereux_henley/rpfm_scraper/core.clj:178` (`update-unit-seeds!`) — your aggregation site |
| Seed-file location | `components/rts-data/resources/rts-data/sql/seed/seed-unit-keys.sql` |
| Seed-file already wired into seed-db | `components/rts-data/src/com/devereux_henley/rts_data/contract.clj` (`seed-files` vector) |
| Migration that adds `unit.key` column | `components/rts-data/resources/rts-data/migrations/000006-create-unit-table.up.sql` (already on the redesign branch) |
| Single-statement seed convention | All `next.jdbc.execute!` callers in this repo only consume one statement; multi-statement seeds silently lose everything after the first `;`. Use a CASE-based UPDATE. |

## Out of scope for this slice

- The migration that adds `unit.key` already lives on the
  `devereux-henley/post-match-redesign` branch (not yet merged). This
  scraper work should land **after** that branch merges so the column
  exists on `main`.
- Cosmetic name + category data already comes from the existing
  `seed-<faction>-units.sql` files; this work only adds the engine-key
  bridge between replay parser output and unit rows.
- Unit portraits are still not bundled. The modal renders text-only
  cards. Adding portraits is a separate effort that involves the
  `assets.clj` copy pipeline + a CDN bucket.
