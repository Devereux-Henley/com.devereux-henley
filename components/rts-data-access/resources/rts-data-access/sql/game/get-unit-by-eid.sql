SELECT
  u.id,
  u.eid,
  u.name,
  u.description,
  g.eid as game_eid,
  ut.eid as unit_type_eid,
  ut.name as unit_type_name,
  uc.eid as unit_category_eid,
  uc.name as unit_category_name,
  json_extract(u.unit_statistics, '$.cost') as cost,
  u.unit_statistics,
  u.version,
  u.created_at,
  u.updated_at,
  u.deleted_at
  FROM
    unit u
    INNER JOIN game g ON g.id = u.game_id
    INNER JOIN unit_type ut ON ut.id = u.unit_type_id
    INNER JOIN unit_category uc ON uc.id = u.unit_category_id
 WHERE u.eid = ?
