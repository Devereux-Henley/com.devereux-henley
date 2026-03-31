SELECT
  uc.id,
  uc.eid,
  uc.name,
  uc.description,
  g.eid as game_eid,
  uc.version,
  uc.created_at,
  uc.updated_at,
  uc.deleted_at
  FROM
    unit_category uc
    INNER JOIN game g ON g.id = uc.game_id
 WHERE uc.eid = ?
