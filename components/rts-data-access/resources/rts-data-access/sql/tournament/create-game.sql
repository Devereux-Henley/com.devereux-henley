INSERT INTO match_game (eid, match_id, game_index, winner_sub, replay_id, uploader_local_alliance_index, created_at)
SELECT
  ?,
  m.id,
  ?,
  ?,
  (SELECT r.id FROM replay r WHERE r.eid = ?),
  ?,
  ?
FROM match m
WHERE m.eid = ?
