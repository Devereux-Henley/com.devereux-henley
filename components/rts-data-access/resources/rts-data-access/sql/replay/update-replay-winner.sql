UPDATE
  replay
SET
  winning_alliance_idx = ?,
  updated_at           = ?
WHERE
  eid = ?
  AND deleted_at IS NULL;
