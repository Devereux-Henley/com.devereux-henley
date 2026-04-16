SELECT
  tr.id,
  tr.eid,
  t.eid AS tournament_eid,
  tr.player_sub,
  tr.registered_at,
  tr.withdrawn_at
FROM
  tournament_registration tr
  INNER JOIN tournament t ON t.id = tr.tournament_id
WHERE t.eid = ?
  AND tr.player_sub = ?
  AND tr.withdrawn_at IS NULL
