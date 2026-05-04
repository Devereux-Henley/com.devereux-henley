-- Inherit mark from family for lore-pinned variant rows that
-- don't yet carry a mark assignment (typically clones produced by
-- the one-shot un-consolidation, whose fabricated eids aren't in
-- seed-unit-keys.sql and therefore miss seed-unit-marks.sql's
-- engine-key→mark CASE).  Looks up the first mark-bearing sibling
-- in the same (family_name, faction_id) and copies its mark over.
-- Runs after seed-unit-marks.sql + seed-unit-lores.sql so the
-- baseline marks and family_names are already in place.
UPDATE unit AS target
SET mark = (
  SELECT u2.mark
  FROM unit u2
  WHERE u2.family_name = target.family_name
    AND u2.faction_id  = target.faction_id
    AND u2.mark IS NOT NULL
  ORDER BY u2.id
  LIMIT 1)
WHERE mark IS NULL
  AND lore IS NOT NULL;
