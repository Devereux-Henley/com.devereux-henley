-- Resolve a vector of engine `factions_tables` keys (e.g.
-- `wh3_dlc23_chd_legion_of_azgorh`) to subfaction rows joined with their
-- parent race (faction).  Used by the post-match parse pipeline to render
-- human-readable faction names in place of the raw engine key.
SELECT
  s.eid       AS eid,
  s.key       AS key,
  s.name      AS name,
  f.eid       AS faction_eid,
  f.name      AS faction_name,
  f.key       AS faction_key
FROM
  subfaction s
  INNER JOIN faction f ON f.id = s.faction_id
WHERE
  s.key IN (%s)
  AND s.deleted_at IS NULL
  AND f.deleted_at IS NULL;
