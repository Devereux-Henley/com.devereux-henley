UPDATE tournament_registration
SET withdrawn_at = ?
WHERE tournament_id = (SELECT id FROM tournament WHERE eid = ?)
  AND player_sub = ?
  AND withdrawn_at IS NULL
