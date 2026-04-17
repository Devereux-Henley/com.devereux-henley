UPDATE match
SET winner_sub = ?,
    status = 'complete',
    updated_at = ?
WHERE eid = ?
  AND status = 'pending'
