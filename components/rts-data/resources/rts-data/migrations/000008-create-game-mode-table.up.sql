CREATE TABLE IF NOT EXISTS game_mode (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  draft_value INTEGER NOT NULL,
  player_count INTEGER NOT NULL,
  reinforcement_value INTEGER NOT NULL,
  reinforcements_enabled INTEGER NOT NULL,
  game_id INTEGER NOT NULL,
  version INTEGER NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  FOREIGN KEY(game_id) REFERENCES game(id)
);
