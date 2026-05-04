-- Lore is now a column on `unit` (see 000031); the unit_lore junction
-- is no longer the source of truth.  Each (mark, lore) variant ships as
-- its own unit row, so we look the lore up by unit.lore directly.
DROP TABLE IF EXISTS unit_lore;
