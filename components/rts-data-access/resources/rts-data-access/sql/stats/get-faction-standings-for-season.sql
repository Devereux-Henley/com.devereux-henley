SELECT
  f.eid              AS faction_eid,
  f.name             AS faction_name,
  COUNT(*)           AS matches_played,
  SUM(CASE WHEN player_won = 1 THEN 1 ELSE 0 END) AS wins,
  SUM(CASE WHEN player_drew = 1 THEN 0 WHEN player_won = 1 THEN 0 ELSE 1 END) AS losses,
  SUM(CASE WHEN player_drew = 1 THEN 1 ELSE 0 END) AS draws
FROM (
  SELECT m.player_one_draft_id AS draft_id,
         (CASE WHEN m.winner_sub = m.player_one_sub THEN 1 ELSE 0 END) AS player_won,
         (CASE WHEN m.winner_sub = 'draw' THEN 1 ELSE 0 END)           AS player_drew
  FROM match m
    INNER JOIN tournament t ON t.id = m.tournament_id
    INNER JOIN season s     ON s.id = t.season_id
  WHERE s.eid = ? AND m.status = 'complete' AND m.player_one_draft_id IS NOT NULL
  UNION ALL
  SELECT m.player_two_draft_id,
         (CASE WHEN m.winner_sub = m.player_two_sub THEN 1 ELSE 0 END),
         (CASE WHEN m.winner_sub = 'draw' THEN 1 ELSE 0 END)
  FROM match m
    INNER JOIN tournament t ON t.id = m.tournament_id
    INNER JOIN season s     ON s.id = t.season_id
  WHERE s.eid = ? AND m.status = 'complete' AND m.player_two_draft_id IS NOT NULL
) AS player_drafts
INNER JOIN draft d   ON d.id = player_drafts.draft_id
INNER JOIN faction f ON f.id = d.faction_id
GROUP BY f.eid, f.name
ORDER BY wins DESC, matches_played DESC, faction_name ASC
