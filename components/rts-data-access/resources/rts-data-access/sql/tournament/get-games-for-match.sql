SELECT
  g.id,
  g.eid,
  m.eid AS match_eid,
  g.game_index,
  g.winner_sub,
  r.eid AS replay_eid,
  g.uploader_local_alliance_index,
  d1.eid AS player_one_draft_eid,
  d2.eid AS player_two_draft_eid,
  g.created_at
FROM
  match_game g
  INNER JOIN match m ON m.id = g.match_id
  LEFT JOIN replay r ON r.id = g.replay_id
  LEFT JOIN draft d1 ON d1.id = g.player_one_draft_id
  LEFT JOIN draft d2 ON d2.id = g.player_two_draft_id
WHERE m.eid = ?
ORDER BY g.game_index ASC
