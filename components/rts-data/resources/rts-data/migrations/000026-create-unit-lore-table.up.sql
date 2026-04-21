CREATE TABLE IF NOT EXISTS unit_lore (
  id INTEGER PRIMARY KEY ASC,
  unit_id INTEGER NOT NULL,
  lore_id INTEGER NOT NULL,
  cost INTEGER NOT NULL DEFAULT 0,
  portrait_key TEXT,
  draftable_spell_keys TEXT,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(unit_id, lore_id, deleted_at),
  FOREIGN KEY(unit_id) REFERENCES unit(id),
  FOREIGN KEY(lore_id) REFERENCES lore(id)
);
