CREATE TABLE IF NOT EXISTS tournament (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  game_id INTEGER NOT NULL,
  league_id INTEGER,
  season_id INTEGER,
  created_by_sub TEXT NOT NULL,
  version INTEGER NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  FOREIGN KEY(game_id) REFERENCES game(id),
  FOREIGN KEY(league_id) REFERENCES league(id),
  FOREIGN KEY(season_id) REFERENCES season(id)
);
CREATE INDEX IF NOT EXISTS idx_tournament_league_id ON tournament(league_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_tournament_season_id ON tournament(season_id) WHERE deleted_at IS NULL;
