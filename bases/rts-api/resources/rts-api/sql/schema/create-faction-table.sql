CREATE TABLE IF NOT EXISTS faction (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  name TEXT NOT NULL,
  game_id INTEGER NOT NULL,
  description TEXT NOT NULL,
  version INT NOT NULL,
  created_by_eid TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(name, game_id, deleted_at),
  FOREIGN KEY(game_id) REFERENCES game(id)
);
