CREATE TABLE IF NOT EXISTS match_game (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  match_id INTEGER NOT NULL,
  game_index INTEGER NOT NULL,
  winner_sub TEXT,
  created_at TEXT NOT NULL,
  UNIQUE(eid),
  FOREIGN KEY(match_id) REFERENCES match(id)
);
