SELECT
  ts.id,
  ts.eid,
  t.eid as tournament_eid,
  ts.tournament_state,
  ts.version,
  ts.created_at,
  ts.updated_at,
  ts.deleted_at
  FROM
    tournament_snapshot ts
    INNER JOIN tournament t ON t.id = ts.tournament_id
 WHERE t.eid = ?
