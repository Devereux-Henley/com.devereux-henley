SELECT
  u.id,
  u.eid,
  u.name,
  u.family_name,
  u.description,
  g.eid as game_eid,
  ut.eid as unit_type_eid,
  ut.name as unit_type_name,
  uc.eid as unit_category_eid,
  uc.name as unit_category_name,
  json_extract(u.unit_statistics, '$.cost') as cost,
  u.unit_statistics,
  u.mark,
  (SELECT COUNT(*) FROM unit fv
    WHERE fv.family_name = u.family_name
      AND fv.faction_id = u.faction_id
      AND fv.deleted_at IS NULL) AS family_variant_count,
  u.is_unique,
  u.version,
  u.created_at,
  u.updated_at,
  u.deleted_at
  FROM
    unit u
    INNER JOIN game g ON g.id = u.game_id
    INNER JOIN faction f ON f.id = u.faction_id
    INNER JOIN unit_type ut ON ut.id = u.unit_type_id
    INNER JOIN unit_category uc ON uc.id = u.unit_category_id
 WHERE f.eid = ?
 ORDER BY uc.id, u.id
