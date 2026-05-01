-- Apply the Mark of Chaos dimension to Warriors of Chaos / Daemons of
-- Chaos units.  Single combined UPDATE because next.jdbc.execute! only
-- consumes the first statement of a multi-statement string (same
-- constraint as seed-unit-keys.sql).  Runs after seed-unit-keys.sql so
-- the engine `key` column is populated and the suffix-based fallback
-- can fire.
--
-- `mark` is derived in this priority order:
--
--   1. `mark_<god>` attribute embedded in `unit_statistics`.  Most
--      Warriors-of-Chaos rank-and-file (Chaos Warriors / Knights /
--      Chosen / Marauders / Sorcerers / Daemon Princes / …) carries
--      this tag.  Mechanical from the engine row.
--
--   2. Engine `land_units` key suffix.  Many Daemons-of-Chaos
--      rank-and-file (Bloodletters / Daemonettes / Plaguebearers /
--      Horrors / …) lack `mark_<god>` because they live under a
--      mono-god subfaction (`_kho_`/`_nur_`/`_sla_`/`_tze_`) and the
--      engine doesn't double-tag them.  WoC Sorcerer & Daemon Prince
--      mark variants carry the suffix `_mkho`/`_mnur`/`_msla`/`_mtze`
--      explicitly — both are caught by the same INSTR checks.
--
-- `name` collapses the explicit mark suffix where the family is a
-- base + per-mark variant pattern ("Daemon Prince of Khorne" →
-- "Daemon Prince", with the mark dimension coming from steps 1/2).
-- Per-faction rank-and-file marked daemons (Bloodletters of
-- Khorne, Plaguebearers of Nurgle, …) keep their flavour names —
-- the family IS the mark for them.
UPDATE unit
SET mark = CASE
  WHEN unit_statistics LIKE '%"mark_khorne"%'    THEN 'khorne'
  WHEN unit_statistics LIKE '%"mark_nurgle"%'    THEN 'nurgle'
  WHEN unit_statistics LIKE '%"mark_slaanesh"%'  THEN 'slaanesh'
  WHEN unit_statistics LIKE '%"mark_tzeentch"%'  THEN 'tzeentch'
  WHEN unit_statistics LIKE '%"mark_undivided"%' THEN 'undivided'
  -- Faction scope: WoC / DoC plus the mono-god factions (Khorne /
  -- Nurgle / Slaanesh / Tzeentch) and Beastmen (whose Khorngors /
  -- Pestigors / Slaangors / Tzaangors carry the mark via the
  -- mark_<god> attribute already, but a key-suffix fallback is
  -- harmless and keeps post-match enrichment consistent across the
  -- duplicate engine-key rows that span faction seeds).
  WHEN faction_id IN (2, 5, 11, 15, 18, 20, 23) AND key IS NOT NULL AND
       (INSTR(key, '_kho_') > 0 OR INSTR(key, '_mkho') > 0 OR INSTR(key, '_khorne') > 0)   THEN 'khorne'
  WHEN faction_id IN (2, 5, 11, 15, 18, 20, 23) AND key IS NOT NULL AND
       (INSTR(key, '_nur_') > 0 OR INSTR(key, '_mnur') > 0 OR INSTR(key, '_nurgle') > 0)   THEN 'nurgle'
  WHEN faction_id IN (2, 5, 11, 15, 18, 20, 23) AND key IS NOT NULL AND
       (INSTR(key, '_sla_') > 0 OR INSTR(key, '_msla') > 0 OR INSTR(key, '_slaanesh') > 0) THEN 'slaanesh'
  WHEN faction_id IN (2, 5, 11, 15, 18, 20, 23) AND key IS NOT NULL AND
       (INSTR(key, '_tze_') > 0 OR INSTR(key, '_mtze') > 0 OR INSTR(key, '_tzeentch') > 0) THEN 'tzeentch'
  -- Mono-god faction fallback: a Khorne/Nurgle/Slaanesh/Tzeentch
  -- faction unit whose key+stats don't tag a mark (e.g. shared
  -- monsters cross-listed under the mono-god faction's seed) inherits
  -- the faction's mark.
  WHEN faction_id = 11 THEN 'khorne'
  WHEN faction_id = 15 THEN 'nurgle'
  WHEN faction_id = 18 THEN 'slaanesh'
  WHEN faction_id = 20 THEN 'tzeentch'
  ELSE mark
END,
name = CASE name
  WHEN 'Daemon Prince of Khorne'         THEN 'Daemon Prince'
  WHEN 'Daemon Prince of Nurgle'         THEN 'Daemon Prince'
  WHEN 'Daemon Prince of Slaanesh'       THEN 'Daemon Prince'
  WHEN 'Daemon Prince of Tzeentch'       THEN 'Daemon Prince'
  WHEN 'Chaos Sorcerer of Nurgle'        THEN 'Chaos Sorcerer'
  WHEN 'Chaos Sorcerer of Slaanesh'      THEN 'Chaos Sorcerer'
  WHEN 'Chaos Sorcerer of Tzeentch'      THEN 'Chaos Sorcerer'
  WHEN 'Chaos Sorcerer Lord of Nurgle'   THEN 'Chaos Sorcerer Lord'
  WHEN 'Chaos Sorcerer Lord of Slaanesh' THEN 'Chaos Sorcerer Lord'
  WHEN 'Chaos Sorcerer Lord of Tzeentch' THEN 'Chaos Sorcerer Lord'
  WHEN 'Chaos Furies (Khorne)'           THEN 'Chaos Furies'
  WHEN 'Chaos Furies (Nurgle)'           THEN 'Chaos Furies'
  WHEN 'Chaos Furies (Slaanesh)'         THEN 'Chaos Furies'
  WHEN 'Chaos Furies (Tzeentch)'         THEN 'Chaos Furies'
  ELSE name
END;
