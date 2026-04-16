SELECT
  te.id,
  te.eid,
  t.eid AS tournament_eid,
  te.player_sub,
  te.created_at,
  te.deleted_at
FROM
  tournament_entry te
  INNER JOIN tournament t ON t.id = te.tournament_id
WHERE t.eid = ?
  AND te.deleted_at IS NULL
ORDER BY te.created_at ASC
