SELECT
  m.id,
  m.eid,
  t.eid AS tournament_eid,
  m.phase_index,
  m.round_index,
  m.player_one_sub,
  m.player_two_sub,
  m.winner_sub,
  m.status,
  m.format,
  m.created_at,
  m.updated_at
FROM
  match m
  INNER JOIN tournament t ON t.id = m.tournament_id
WHERE t.eid = ?
  AND m.phase_index = ?
  AND m.round_index = ?
ORDER BY m.id ASC
