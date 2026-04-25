-- SQLite < 3.35 cannot DROP COLUMN; rebuild without the new columns.
CREATE TABLE match_game_new (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  match_id INTEGER NOT NULL,
  game_index INTEGER NOT NULL,
  winner_sub TEXT,
  created_at TEXT NOT NULL,
  UNIQUE(eid),
  FOREIGN KEY(match_id) REFERENCES match(id)
);
--;;
INSERT INTO match_game_new (id, eid, match_id, game_index, winner_sub, created_at)
SELECT id, eid, match_id, game_index, winner_sub, created_at FROM match_game;
--;;
DROP TABLE match_game;
--;;
ALTER TABLE match_game_new RENAME TO match_game;
--;;
DROP INDEX IF EXISTS idx_replay_match_id_external;
--;;
DROP INDEX IF EXISTS idx_replay_uploaded_by_sub;
--;;
DROP TABLE IF EXISTS replay;
