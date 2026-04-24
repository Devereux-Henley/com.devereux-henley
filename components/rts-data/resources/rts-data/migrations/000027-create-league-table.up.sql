CREATE TABLE IF NOT EXISTS league (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  game_id INTEGER NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  created_by_sub TEXT NOT NULL,
  version INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  FOREIGN KEY(game_id) REFERENCES game(id)
);
CREATE INDEX IF NOT EXISTS idx_league_game_id ON league(game_id) WHERE deleted_at IS NULL;
