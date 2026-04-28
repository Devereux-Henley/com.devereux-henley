DROP INDEX IF EXISTS idx_subfaction_key;
--;;
DROP INDEX IF EXISTS idx_subfaction_faction_id;
--;;
DROP TABLE IF EXISTS subfaction;
--;;
DROP INDEX IF EXISTS idx_faction_key_per_game;
--;;
ALTER TABLE faction DROP COLUMN key;
