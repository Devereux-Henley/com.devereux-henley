CREATE TABLE IF NOT EXISTS tournament_entry (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  tournament_id INTEGER NOT NULL,
  player_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  UNIQUE(eid),
  UNIQUE(tournament_id, player_sub),
  FOREIGN KEY(tournament_id) REFERENCES tournament(id)
);
