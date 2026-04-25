SELECT
  g.id,
  g.eid,
  m.eid AS match_eid,
  g.game_index,
  g.winner_sub,
  g.replay_id,
  r.eid AS replay_eid,
  g.uploader_local_alliance_idx,
  g.created_at
FROM
  match_game g
  INNER JOIN match m ON m.id = g.match_id
  LEFT JOIN replay r ON r.id = g.replay_id
WHERE m.eid = ?
ORDER BY g.game_index ASC
