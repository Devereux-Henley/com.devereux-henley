SELECT
  t.id,
  t.eid,
  t.name,
  t.description,
  g.eid AS game_eid,
  l.eid AS league_eid,
  s.eid AS season_eid,
  t.created_by_sub,
  t.version,
  t.created_at,
  t.updated_at,
  t.deleted_at
FROM
  tournament t
  INNER JOIN game g ON g.id = t.game_id
  LEFT JOIN league l ON l.id = t.league_id
  LEFT JOIN season s ON s.id = t.season_id
WHERE g.eid = ?
  AND t.deleted_at IS NULL
ORDER BY t.created_at DESC
