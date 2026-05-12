INSERT INTO match_game (eid, match_id, game_index, winner_sub, replay_id,
                        uploader_local_alliance_index,
                        player_one_draft_id, player_two_draft_id,
                        created_at)
SELECT
  ?,
  m.id,
  ?,
  ?,
  (SELECT r.id  FROM replay r WHERE r.eid = ?),
  ?,
  (SELECT d1.id FROM draft d1 WHERE d1.eid = ?),
  (SELECT d2.id FROM draft d2 WHERE d2.eid = ?),
  ?
FROM match m
WHERE m.eid = ?
