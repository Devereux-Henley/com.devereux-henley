SELECT
  t.id,
  t.eid,
  g.eid as game_eid,
  t.title,
  t.description,
  t.tournament_start_datetime,
  t.tournament_checkin_datetime,
  t.tournament_type,
  t.competitor_type,
  t.version,
  t.created_at,
  t.updated_at,
  t.deleted_at
  FROM
    tournament t
    INNER JOIN game g ON g.id = t.game_id
WHERE t.created_at <= ?
ORDER BY t.created_at DESC
LIMIT ?
OFFSET ?
