CREATE TABLE IF NOT EXISTS draft (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  name TEXT,
  game_mode_id INTEGER NOT NULL,
  faction_id INTEGER NOT NULL,
  player_sub TEXT NOT NULL,
  version INTEGER NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  FOREIGN KEY(game_mode_id) REFERENCES game_mode(id),
  FOREIGN KEY(faction_id) REFERENCES faction(id)
);
