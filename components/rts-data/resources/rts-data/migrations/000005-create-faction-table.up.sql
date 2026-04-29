CREATE TABLE IF NOT EXISTS faction (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  name TEXT NOT NULL,
  key TEXT,
  game_id INTEGER NOT NULL,
  description TEXT NOT NULL,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(name, game_id, deleted_at),
  FOREIGN KEY(game_id) REFERENCES game(id)
);
--;;
CREATE UNIQUE INDEX IF NOT EXISTS idx_faction_key_per_game ON faction(game_id, key) WHERE key IS NOT NULL AND deleted_at IS NULL;
