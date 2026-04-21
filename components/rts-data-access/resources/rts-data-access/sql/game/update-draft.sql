UPDATE draft
SET name = ?,
    updated_at = ?
WHERE eid = ?
  AND deleted_at IS NULL
