CREATE TABLE IF NOT EXISTS game_social_link (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  url TEXT NOT NULL,
  game_id INTEGER NOT NULL,
  social_media_platform_id INTEGER NOT NULL,
  version INTEGER NOT NULL,
  created_by_eid TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(game_id, social_media_platform_id, deleted_at),
  FOREIGN KEY(game_id) REFERENCES game(id),
  FOREIGN KEY(social_media_platform_id) REFERENCES social_media_platform(id)
);
