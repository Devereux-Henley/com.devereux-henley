CREATE TABLE IF NOT EXISTS item (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  key TEXT NOT NULL,
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  cost INTEGER NOT NULL DEFAULT 0,
  game_id INTEGER NOT NULL,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(key, game_id, deleted_at),
  FOREIGN KEY(game_id) REFERENCES game(id)
);
