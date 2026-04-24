UPDATE league
SET name = ?,
    description = ?,
    version = version + 1,
    updated_at = ?
WHERE eid = ?
  AND deleted_at IS NULL
