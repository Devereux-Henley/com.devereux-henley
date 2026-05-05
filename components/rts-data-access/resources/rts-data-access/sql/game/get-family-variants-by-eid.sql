-- Returns every unit row that shares the given unit's family (same
-- family_name + faction).  Drives the Mark of Chaos and Lore of Magic
-- selectors in the draft unit panel: a player browsing "Daemon Prince"
-- can flip between marked/lored variants without leaving the panel.
-- The engine `name` differs across variants ("Daemon Prince of Khorne"
-- vs base "Daemon Prince" vs "Archmage (High)" vs "Archmage (Light)");
-- `family_name` is the shared key after stripping mark and lore
-- suffixes (see marks_seed.clj `chained-replaces`).  Mono-variant
-- families return a single row; the panel uses the count to decide
-- whether to render a selector at all.
SELECT
  fv.eid,
  fv.mark,
  fv.lore,
  fv.name,
  json_extract(fv.unit_statistics, '$.cost') AS cost
FROM
  unit u
  INNER JOIN unit fv
          ON fv.family_name = u.family_name
         AND fv.faction_id = u.faction_id
         AND fv.deleted_at IS NULL
WHERE
  u.eid = ?
ORDER BY fv.id;
