CREATE TABLE IF NOT EXISTS mount (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  key TEXT NOT NULL,
  name TEXT NOT NULL,
  icon_key TEXT,
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
