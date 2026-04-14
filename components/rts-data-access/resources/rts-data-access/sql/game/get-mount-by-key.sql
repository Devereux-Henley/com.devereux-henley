SELECT
  id,
  eid,
  key,
  name,
  icon_key,
  0 AS cost
  FROM mount
 WHERE key = ?
   AND deleted_at IS NULL
 LIMIT 1
