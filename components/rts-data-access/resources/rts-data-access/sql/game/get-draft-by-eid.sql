SELECT
  d.id,
  d.eid,
  d.name,
  gm.eid AS game_mode_eid,
  f.eid AS faction_eid,
  f.name AS faction_name,
  strftime('%m/%d/%Y', d.created_at) AS created_at_display,
  d.player_sub,
  d.version,
  d.created_at,
  d.updated_at,
  d.deleted_at
FROM
  draft d
  INNER JOIN game_mode gm ON gm.id = d.game_mode_id
  INNER JOIN faction f ON f.id = d.faction_id
WHERE d.eid = ?
