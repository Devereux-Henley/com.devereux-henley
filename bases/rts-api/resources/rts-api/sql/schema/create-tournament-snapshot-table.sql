CREATE TABLE IF NOT EXISTS tournament_snapshot (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  tournament_id INTEGER NOT NULL,
  tournament_state TEXT NOT NULL,
  version INT NOT NULL,
  created_by_eid TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  FOREIGN KEY(tournament_id) REFERENCES tournament(id)
);
