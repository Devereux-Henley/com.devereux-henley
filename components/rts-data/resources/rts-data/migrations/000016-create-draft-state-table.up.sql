CREATE TABLE IF NOT EXISTS draft_state (
  id INTEGER PRIMARY KEY ASC,
  draft_id INTEGER NOT NULL,
  state TEXT NOT NULL DEFAULT '{}',
  updated_at TEXT NOT NULL,
  UNIQUE(draft_id),
  FOREIGN KEY(draft_id) REFERENCES draft(id)
);
