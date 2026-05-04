-- SQLite < 3.35 cannot DROP COLUMN.  Migratus needs at least one
-- statement, so this is a CREATE-IF-NOT-EXISTS no-op against the
-- already-present unit table — runs without errors and without
-- altering schema.
CREATE TABLE IF NOT EXISTS unit (id INTEGER);
