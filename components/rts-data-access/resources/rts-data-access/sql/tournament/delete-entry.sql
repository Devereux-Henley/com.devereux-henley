DELETE FROM tournament_entry
WHERE tournament_id = (SELECT id FROM tournament WHERE eid = ?)
  AND player_sub = ?
