SELECT
  s.id,
  s.eid,
  s.key,
  s.name,
  s.description,
  s.spell_type,
  s.mana_cost,
  s.cost,
  s.game_id
  FROM spell s
  JOIN spell_lore sl ON sl.spell_id = s.id
  JOIN lore l ON l.id = sl.lore_id
 WHERE l.key = ?
   AND s.deleted_at IS NULL
   AND sl.deleted_at IS NULL
   AND l.deleted_at IS NULL
 ORDER BY s.name
