CREATE TABLE IF NOT EXISTS player_checkin (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  player_id INTEGER NOT NULL,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(player_id, deleted_at),
  FOREIGN KEY(player_id) REFERENCES player(id)
);
