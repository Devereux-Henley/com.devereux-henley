INSERT INTO match_game (eid, match_id, game_index, winner_sub, created_at)
SELECT ?, m.id, ?, ?, ?
FROM match m
WHERE m.eid = ?
