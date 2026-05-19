SELECT
  f.id,
  f.eid,
  f.name,
  f.description,
  g.eid as game_eid,
  f.version,
  f.created_at,
  f.updated_at,
  f.deleted_at
  FROM
    faction f
    INNER JOIN game g ON g.id = f.game_id
 WHERE f.deleted_at IS NULL
 ORDER BY g.name, f.name
