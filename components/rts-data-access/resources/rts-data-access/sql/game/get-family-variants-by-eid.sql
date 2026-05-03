-- Returns every unit row that shares the given unit's family (same
-- family_name + faction).  Drives the Mark of Chaos selector in the
-- draft unit panel so a player browsing "Daemon Prince" can flip
-- between the four marked variants without leaving the panel.  The
-- engine `name` differs across mark variants ("Daemon Prince of
-- Khorne" vs base "Daemon Prince"); `family_name` is the shared key.
-- Mono-variant families return a single row; the panel uses the count
-- to decide whether to render a selector at all.
SELECT
  fv.eid,
  fv.mark,
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
