SELECT
  ut.id,
  ut.eid,
  ut.name,
  ut.description,
  g.eid as game_eid,
  ut.version,
  ut.created_at,
  ut.updated_at,
  ut.deleted_at
  FROM
    unit_type ut
    INNER JOIN game g ON g.id = ut.game_id
 WHERE ut.eid = ?
