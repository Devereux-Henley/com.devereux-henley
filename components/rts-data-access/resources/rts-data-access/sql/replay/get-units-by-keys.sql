-- Resolve a vector of engine `land_units` keys to unit rows.  Used by the
-- post-match parse pipeline to enrich each parsed unit key with display
-- name + cost + category for rendering in the review modal.
SELECT
  u.eid,
  u.key,
  u.name,
  u.family_name,
  u.mark,
  (SELECT COUNT(*) FROM unit fv
    WHERE fv.family_name = u.family_name
      AND fv.faction_id = u.faction_id
      AND fv.deleted_at IS NULL) AS family_variant_count,
  json_extract(u.unit_statistics, '$.cost') AS cost,
  uc.name AS unit_category_name,
  ut.name AS unit_type_name,
  f.eid   AS faction_eid,
  f.name  AS faction_name
FROM
  unit u
  INNER JOIN unit_category uc ON uc.id = u.unit_category_id
  INNER JOIN unit_type ut     ON ut.id = u.unit_type_id
  INNER JOIN faction f        ON f.id  = u.faction_id
WHERE
  u.key IN (%s)
  AND u.deleted_at IS NULL;
