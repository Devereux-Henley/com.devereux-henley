CREATE TABLE IF NOT EXISTS tournament (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  game_id INTEGER NOT NULL,
  title TEXT NOT NULL,
  description TEXT NOT NULL,
  tournament_start_datetime TEXT NOT NULL,
  tournament_checkin_datetime TEXT NOT NULL,
  tournament_type TEXT NOT NULL,
  competitor_type TEXT NOT NULL,
  version INT NOT NULL,
  created_by_eid TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  FOREIGN KEY(game_id) REFERENCES game(id)
);
