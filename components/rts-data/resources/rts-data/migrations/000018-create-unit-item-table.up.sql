CREATE TABLE IF NOT EXISTS unit_item (
  id INTEGER PRIMARY KEY ASC,
  unit_id INTEGER NOT NULL,
  item_id INTEGER NOT NULL,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(unit_id, item_id, deleted_at),
  FOREIGN KEY(unit_id) REFERENCES unit(id),
  FOREIGN KEY(item_id) REFERENCES item(id)
);
