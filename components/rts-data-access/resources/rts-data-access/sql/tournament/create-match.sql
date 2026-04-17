INSERT INTO match (eid, tournament_id, phase_index, round_index, player_one_sub, player_two_sub, status, created_at, updated_at)
SELECT ?, t.id, ?, ?, ?, ?, 'pending', ?, ?
FROM tournament t
WHERE t.eid = ?
