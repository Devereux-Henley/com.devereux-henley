SELECT
  te.id,
  te.eid,
  t.eid AS tournament_eid,
  te.player_sub,
  te.created_at
FROM
  tournament_entry te
  INNER JOIN tournament t ON t.id = te.tournament_id
WHERE t.eid = ?
ORDER BY te.created_at ASC
