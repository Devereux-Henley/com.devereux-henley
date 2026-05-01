-- Returns every unit row that shares the given unit's family (same
-- name + faction).  Drives the Mark of Chaos selector in the draft
-- unit panel so a player browsing "Daemon Prince" can flip between
-- the four marked variants without leaving the panel.  Mono-variant
-- families return a single row; the panel uses the count to decide
-- whether to render a selector at all.
SELECT
  fv.eid,
  fv.mark,
  json_extract(fv.unit_statistics, '$.cost') AS cost
FROM
  unit u
  INNER JOIN unit fv
          ON fv.name = u.name
         AND fv.faction_id = u.faction_id
         AND fv.deleted_at IS NULL
WHERE
  u.eid = ?
ORDER BY fv.id;
