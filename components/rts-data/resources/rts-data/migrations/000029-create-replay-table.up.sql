CREATE TABLE IF NOT EXISTS replay (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  match_id TEXT NOT NULL,
  played_at TEXT NOT NULL,
  victory_condition TEXT,
  parser_format TEXT NOT NULL,
  parsed_json TEXT NOT NULL,
  winning_alliance_idx INTEGER,
  uploaded_by_sub TEXT NOT NULL,
  version INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(match_id, deleted_at)
);
CREATE INDEX IF NOT EXISTS idx_replay_uploaded_by_sub ON replay(uploaded_by_sub) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_replay_played_at ON replay(played_at) WHERE deleted_at IS NULL;
