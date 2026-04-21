SELECT
  m.id,
  m.eid,
  m.key,
  m.name,
  m.icon_key,
  um.cost,
  um.stats_override,
  um.granted_ability_keys
  FROM mount m
  JOIN unit_mount um ON um.mount_id = m.id
  JOIN unit u ON u.id = um.unit_id
 WHERE u.eid = ?
   AND um.deleted_at IS NULL
   AND m.deleted_at IS NULL
 ORDER BY um.cost, m.name
