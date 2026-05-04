-- Lore-of-Magic dimension on the unit row, parallel to `mark`.  Wizards
-- with multiple lore options ship as one row per (mark, lore) variant
-- (e.g. "Archmage (High)" and "Archmage (Light)" share family_name
-- "Archmage" but differ here).  Non-spellcasters and single-lore
-- wizards leave it NULL.  Populated by seed-unit-lores.sql (a CASE
-- UPDATE keyed by eid, mirroring seed-unit-marks.sql).  Not CHECK-
-- constrained because the valid set is the engine `lore.key` catalogue
-- — too large to hand-maintain and kept in sync via the lore table FK
-- pattern at the application layer instead.
ALTER TABLE unit ADD COLUMN lore TEXT;
