CREATE TABLE IF NOT EXISTS match (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  tournament_id INTEGER NOT NULL,
  phase_index INTEGER NOT NULL,
  round_index INTEGER NOT NULL,
  player_one_sub TEXT NOT NULL,
  player_two_sub TEXT,
  winner_sub TEXT,
  status TEXT NOT NULL DEFAULT 'pending',
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  UNIQUE(eid),
  FOREIGN KEY(tournament_id) REFERENCES tournament(id)
);
