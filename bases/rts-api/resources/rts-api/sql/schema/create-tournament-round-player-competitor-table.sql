CREATE TABLE IF NOT EXISTS tournament_round_player_competitor (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  tournament_round_id INTEGER NOT NULL,
  player_id INTEGER NOT NULL,
  ordinal INTEGER NOT NULL,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(tournament_round_id, player_id, deleted_at),
  FOREIGN KEY(tournament_round_id) REFERENCES tournament_round(id),
  FOREIGN KEY(player_id) REFERENCES player(id)
);
