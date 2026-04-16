INSERT INTO tournament_registration (eid, tournament_id, player_sub, registered_at)
SELECT ?, t.id, ?, ?
FROM tournament t
WHERE t.eid = ?
