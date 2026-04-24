SELECT
  s.id,
  s.eid,
  l.eid AS league_eid,
  s.ordinal,
  s.name,
  s.start_at,
  s.end_at,
  s.version,
  s.created_at,
  s.updated_at,
  s.deleted_at
FROM
  season s
  INNER JOIN league l ON l.id = s.league_id
WHERE s.eid = ?
