-- Replays uploaded to record a tournament match's per-game outcomes.
-- One row per uploaded `.replay` file; linked to a `match_game` row via
-- `match_game.replay_id`.  The full parser JSON is kept verbatim so the
-- post-match modal can render parsed drafts without re-parsing.
CREATE TABLE IF NOT EXISTS replay (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  match_id_external TEXT NOT NULL,
  played_at TEXT NOT NULL,
  victory_condition TEXT,
  parser_format TEXT NOT NULL,
  parsed_json TEXT NOT NULL,
  uploader_local_alliance_idx INTEGER,
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
ALTER TABLE match_game ADD COLUMN replay_id INTEGER REFERENCES replay(id);
--;;
ALTER TABLE match_game ADD COLUMN uploader_local_alliance_idx INTEGER;
