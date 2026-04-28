ALTER TABLE faction ADD COLUMN key TEXT;
--;;
CREATE UNIQUE INDEX IF NOT EXISTS idx_faction_key_per_game ON faction(game_id, key) WHERE key IS NOT NULL AND deleted_at IS NULL;
--;;
CREATE TABLE IF NOT EXISTS subfaction (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  key TEXT NOT NULL,
  name TEXT NOT NULL,
  faction_id INTEGER NOT NULL,
  version INTEGER NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(key, deleted_at),
  FOREIGN KEY(faction_id) REFERENCES faction(id)
);
--;;
CREATE INDEX IF NOT EXISTS idx_subfaction_faction_id ON subfaction(faction_id) WHERE deleted_at IS NULL;
--;;
CREATE INDEX IF NOT EXISTS idx_subfaction_key ON subfaction(key) WHERE deleted_at IS NULL;
