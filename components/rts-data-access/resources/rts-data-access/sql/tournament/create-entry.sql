INSERT INTO tournament_entry (eid, tournament_id, player_sub, created_at)
SELECT ?, t.id, ?, ?
FROM tournament t
WHERE t.eid = ?
