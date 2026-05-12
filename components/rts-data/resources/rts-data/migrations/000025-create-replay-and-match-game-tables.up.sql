-- Replay must exist before match_game declares its FK to it.
CREATE TABLE IF NOT EXISTS replay (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  match_id_external TEXT NOT NULL,
  played_at TEXT NOT NULL,
  victory_condition TEXT,
  parser_format TEXT NOT NULL,
  parsed_json TEXT NOT NULL,
  uploader_local_alliance_index INTEGER,
  uploaded_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid)
);
--;;
CREATE INDEX IF NOT EXISTS idx_replay_match_id_external ON replay(match_id_external) WHERE deleted_at IS NULL;
--;;
CREATE INDEX IF NOT EXISTS idx_replay_uploaded_by_sub ON replay(uploaded_by_sub) WHERE deleted_at IS NULL;
--;;
CREATE TABLE IF NOT EXISTS match_game (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  match_id INTEGER NOT NULL,
  game_index INTEGER NOT NULL,
  winner_sub TEXT,
  replay_id INTEGER REFERENCES replay(id),
  uploader_local_alliance_index INTEGER,
  player_one_draft_id INTEGER REFERENCES draft(id),
  player_two_draft_id INTEGER REFERENCES draft(id),
  created_at TEXT NOT NULL,
  UNIQUE(eid),
  FOREIGN KEY(match_id) REFERENCES match(id)
);
