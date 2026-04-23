SELECT
  m.id,
  m.eid,
  t.eid AS tournament_eid,
  m.phase_index,
  m.round_index,
  m.bracket_type,
  m.player_one_sub,
  m.player_two_sub,
  d1.eid AS player_one_draft_eid,
  d2.eid AS player_two_draft_eid,
  m.winner_sub,
  m.status,
  m.format,
  m.created_at,
  m.updated_at
FROM
  match m
  INNER JOIN tournament t ON t.id = m.tournament_id
  LEFT JOIN draft d1 ON d1.id = m.player_one_draft_id
  LEFT JOIN draft d2 ON d2.id = m.player_two_draft_id
WHERE m.eid = ?
