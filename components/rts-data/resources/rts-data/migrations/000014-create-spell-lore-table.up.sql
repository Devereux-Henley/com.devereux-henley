CREATE TABLE IF NOT EXISTS spell_lore (
  id INTEGER PRIMARY KEY ASC,
  eid TEXT NOT NULL,
  spell_id INTEGER NOT NULL,
  lore_id INTEGER NOT NULL,
  game_id INTEGER NOT NULL,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(eid),
  UNIQUE(spell_id, lore_id, deleted_at),
  FOREIGN KEY(spell_id) REFERENCES spell(id),
  FOREIGN KEY(lore_id) REFERENCES lore(id),
  FOREIGN KEY(game_id) REFERENCES game(id)
);
