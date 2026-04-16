SELECT
  t.id,
  t.eid,
  t.name,
  t.description,
  g.eid AS game_eid,
  t.created_by_sub,
  t.version,
  t.created_at,
  t.updated_at,
  t.deleted_at
FROM
  tournament t
  INNER JOIN game g ON g.id = t.game_id
WHERE t.eid = ?
