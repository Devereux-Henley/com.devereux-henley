CREATE TABLE IF NOT EXISTS tournament (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  game_id INTEGER NOT NULL,
  created_by_sub TEXT NOT NULL,
  version INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  FOREIGN KEY(game_id) REFERENCES game(id)
);
