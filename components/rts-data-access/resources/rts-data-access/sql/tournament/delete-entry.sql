UPDATE tournament_entry
SET deleted_at = ?
WHERE tournament_id = (SELECT id FROM tournament WHERE eid = ?)
  AND player_sub = ?
  AND deleted_at IS NULL
