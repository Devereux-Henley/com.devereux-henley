CREATE TABLE IF NOT EXISTS tournament_following (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  user_sub TEXT NOT NULL,
  tournament_id INTEGER NOT NULL,
  created_by_eid TEXT NOT NULL,
  created_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(user_sub, tournament_id, deleted_at)
  FOREIGN KEY(tournament_id) REFERENCES tournament(id)
);
