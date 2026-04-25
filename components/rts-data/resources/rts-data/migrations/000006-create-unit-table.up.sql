CREATE TABLE IF NOT EXISTS unit (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  -- Engine `land_units` key (e.g. wh_main_emp_inf_halberdiers). Populated by
  -- the rpfm scraper at seed time so that parsed `.replay` files can join
  -- their per-unit keys back to a unit row.
  key TEXT,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  game_id INTEGER NOT NULL,
  faction_id INTEGER NOT NULL,
  unit_type_id INTEGER NOT NULL,
  unit_category_id INTEGER NOT NULL,
  unit_statistics TEXT NOT NULL,
  is_unique INTEGER NOT NULL DEFAULT 0,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(name, game_id, deleted_at),
  FOREIGN KEY(game_id) REFERENCES game(id),
  FOREIGN KEY(faction_id) REFERENCES faction(id),
  FOREIGN KEY(unit_type_id) REFERENCES unit_type(id),
  FOREIGN KEY(unit_category_id) REFERENCES unit_category(id)
);
--;;
CREATE UNIQUE INDEX IF NOT EXISTS idx_unit_key ON unit(key) WHERE key IS NOT NULL AND deleted_at IS NULL;
