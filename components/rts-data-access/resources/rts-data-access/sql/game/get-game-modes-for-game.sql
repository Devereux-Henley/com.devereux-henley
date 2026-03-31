SELECT
  gm.id,
  gm.eid,
  gm.name,
  gm.description,
  gm.draft_value,
  gm.player_count,
  gm.reinforcement_value,
  gm.reinforcements_enabled,
  g.eid as game_eid,
  gm.version,
  gm.created_at,
  gm.updated_at,
  gm.deleted_at
FROM
  game_mode gm
  INNER JOIN game g ON g.id = gm.game_id
WHERE g.eid = ?
ORDER BY gm.id
