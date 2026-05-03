CREATE TABLE IF NOT EXISTS unit (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  -- Engine `land_units` key (e.g. wh_main_emp_inf_halberdiers). Populated by
  -- the rpfm scraper at seed time so that parsed `.replay` files can join
  -- their per-unit keys back to a unit row.
  key TEXT,
  name TEXT NOT NULL,
  -- Family identifier shared across mark variants (e.g. all four marked
  -- "Daemon Prince of <God>" rows + the unmarked base share family_name
  -- "Daemon Prince").  The roster card groups by this; the slot/panel/
  -- replays display `name`, which retains the engine-original full string
  -- including any " of <God>" suffix.  Populated by seed-unit-marks.sql
  -- for every row (defaults to the row's own name for non-mark families)
  -- — left NULL-able only so the per-faction unit seeds don't have to
  -- write the column themselves.
  family_name TEXT,
  description TEXT NOT NULL,
  game_id INTEGER NOT NULL,
  faction_id INTEGER NOT NULL,
  unit_type_id INTEGER NOT NULL,
  unit_category_id INTEGER NOT NULL,
  unit_statistics TEXT NOT NULL,
  -- Mark of Chaos dimension for Warriors of Chaos / Daemons of Chaos units.
  -- Per-mark variants share a display name (e.g. "Daemon Prince") and are
  -- distinguished by this column; non-Chaos units leave it NULL.
  mark TEXT CHECK (mark IS NULL OR mark IN ('khorne', 'nurgle', 'slaanesh', 'tzeentch', 'undivided')),
  is_unique INTEGER NOT NULL DEFAULT 0,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(name, game_id, faction_id, mark, deleted_at),
  FOREIGN KEY(game_id) REFERENCES game(id),
  FOREIGN KEY(faction_id) REFERENCES faction(id),
  FOREIGN KEY(unit_type_id) REFERENCES unit_type(id),
  FOREIGN KEY(unit_category_id) REFERENCES unit_category(id)
);
--;;
-- Non-unique: shared units (Regiments of Renown like Gotrek & Felix) appear
-- in multiple faction seeds with the same engine key but different unit eids.
-- The post-match join collapses duplicates by key client-side; the index is
-- here for lookup speed only.
CREATE INDEX IF NOT EXISTS idx_unit_key ON unit(key) WHERE key IS NOT NULL AND deleted_at IS NULL;
--;;
CREATE INDEX IF NOT EXISTS idx_unit_mark ON unit(mark) WHERE mark IS NOT NULL AND deleted_at IS NULL;
