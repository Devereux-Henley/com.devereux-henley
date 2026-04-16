CREATE TABLE IF NOT EXISTS tournament_state (
  id INTEGER PRIMARY KEY ASC,
  tournament_id INTEGER NOT NULL,
  state TEXT NOT NULL DEFAULT '{}',
  updated_at TEXT NOT NULL,
  UNIQUE(tournament_id),
  FOREIGN KEY(tournament_id) REFERENCES tournament(id)
);
