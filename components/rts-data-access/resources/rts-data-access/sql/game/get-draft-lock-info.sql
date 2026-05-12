SELECT
  m.eid       AS match_eid,
  t.eid       AS tournament_eid,
  t.name      AS tournament_name
FROM draft d
  INNER JOIN match_game mg
          ON mg.player_one_draft_id = d.id
          OR mg.player_two_draft_id = d.id
  INNER JOIN match m
          ON m.id = mg.match_id
  INNER JOIN tournament t
          ON t.id = m.tournament_id
WHERE d.eid = ?
ORDER BY mg.created_at ASC
LIMIT 1
