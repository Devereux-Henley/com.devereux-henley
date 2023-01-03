CREATE TABLE IF NOT EXISTS player (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  tournament_id INTEGER NOT NULL,
  player_sub TEXT NOT NULL,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(player_sub, game_id, deleted_at),
  FOREIGN KEY(tournament_id) REFERENCES tournament(id)
);
