SELECT
  l.id,
  l.eid,
  g.eid AS game_eid,
  l.name,
  l.description,
  l.created_by_sub,
  l.version,
  l.created_at,
  l.updated_at,
  l.deleted_at
FROM
  league l
  INNER JOIN game g ON g.id = l.game_id
WHERE g.eid = ?
  AND l.deleted_at IS NULL
ORDER BY l.name ASC
