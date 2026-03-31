SELECT
  d.id,
  d.eid,
  gm.eid AS game_mode_eid,
  f.eid AS faction_eid,
  d.player_sub,
  d.version,
  d.created_at,
  d.updated_at,
  d.deleted_at
FROM
  draft d
  INNER JOIN game_mode gm ON gm.id = d.game_mode_id
  INNER JOIN faction f ON f.id = d.faction_id
WHERE d.player_sub = ?
ORDER BY d.id
