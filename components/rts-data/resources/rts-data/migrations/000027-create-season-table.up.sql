CREATE TABLE IF NOT EXISTS season (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  league_id INTEGER NOT NULL,
  ordinal INTEGER NOT NULL,
  name TEXT,
  start_at TEXT NOT NULL,
  end_at TEXT NOT NULL,
  version INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(league_id, ordinal),
  FOREIGN KEY(league_id) REFERENCES league(id)
);
CREATE INDEX IF NOT EXISTS idx_season_league_id ON season(league_id) WHERE deleted_at IS NULL;
