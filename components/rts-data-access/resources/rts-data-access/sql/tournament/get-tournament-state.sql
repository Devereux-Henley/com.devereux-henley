SELECT
  ts.id,
  ts.state,
  ts.updated_at
FROM
  tournament_state ts
  INNER JOIN tournament t ON t.id = ts.tournament_id
WHERE t.eid = ?
