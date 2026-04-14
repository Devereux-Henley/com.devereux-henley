SELECT
  i.id,
  i.eid,
  i.key,
  i.name,
  i.category,
  i.cost,
  i.icon_key
  FROM item i
  JOIN unit_item ui ON ui.item_id = i.id
  JOIN unit u ON u.id = ui.unit_id
 WHERE u.eid = ?
   AND ui.deleted_at IS NULL
   AND i.deleted_at IS NULL
 ORDER BY i.category, i.name
