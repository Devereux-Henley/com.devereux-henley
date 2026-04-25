INSERT INTO match_game (eid, match_id, game_index, winner_sub, replay_id, uploader_local_alliance_idx, created_at)
SELECT ?, m.id, ?, ?, ?, ?, ?
FROM match m
WHERE m.eid = ?
