SELECT
  l.id,
  l.eid,
  l.key,
  l.name,
  ul.cost,
  ul.portrait_key
  FROM lore l
  JOIN unit_lore ul ON ul.lore_id = l.id
  JOIN unit u ON u.id = ul.unit_id
 WHERE u.eid = ?
   AND ul.deleted_at IS NULL
   AND l.deleted_at IS NULL
 ORDER BY ul.id
