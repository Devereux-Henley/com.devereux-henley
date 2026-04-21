SELECT
  id,
  eid,
  key,
  name,
  icon_key,
  0 AS cost,
  null AS stats_override,
  null AS granted_ability_keys
  FROM mount
 WHERE key = ?
   AND deleted_at IS NULL
 LIMIT 1
