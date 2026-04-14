CREATE TABLE IF NOT EXISTS unit_mount (
  id INTEGER PRIMARY KEY ASC,
  unit_id INTEGER NOT NULL,
  mount_id INTEGER NOT NULL,
  cost INTEGER NOT NULL DEFAULT 0,
  version INT NOT NULL,
  created_by_sub TEXT NOT NULL,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  deleted_at TEXT,
  UNIQUE(unit_id, mount_id, deleted_at),
  FOREIGN KEY(unit_id) REFERENCES unit(id),
  FOREIGN KEY(mount_id) REFERENCES mount(id)
);
