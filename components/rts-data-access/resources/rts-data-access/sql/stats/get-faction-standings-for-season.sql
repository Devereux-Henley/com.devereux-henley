SELECT
  f.eid              AS faction_eid,
  f.name             AS faction_name,
  COUNT(*)           AS matches_played,
  SUM(CASE WHEN player_won = 1 THEN 1 ELSE 0 END) AS wins,
  SUM(CASE WHEN player_won = 1 THEN 0 ELSE 1 END) AS losses
FROM (
  SELECT mg.player_one_draft_id AS draft_id,
         (CASE WHEN mg.winner_sub = m.player_one_sub THEN 1 ELSE 0 END) AS player_won
  FROM match_game mg
    INNER JOIN match m      ON m.id = mg.match_id
    INNER JOIN tournament t ON t.id = m.tournament_id
    INNER JOIN season s     ON s.id = t.season_id
  WHERE s.eid = ? AND mg.winner_sub IS NOT NULL AND mg.player_one_draft_id IS NOT NULL
  UNION ALL
  SELECT mg.player_two_draft_id,
         (CASE WHEN mg.winner_sub = m.player_two_sub THEN 1 ELSE 0 END)
  FROM match_game mg
    INNER JOIN match m      ON m.id = mg.match_id
    INNER JOIN tournament t ON t.id = m.tournament_id
    INNER JOIN season s     ON s.id = t.season_id
  WHERE s.eid = ? AND mg.winner_sub IS NOT NULL AND mg.player_two_draft_id IS NOT NULL
) AS player_drafts
INNER JOIN draft d   ON d.id = player_drafts.draft_id
INNER JOIN faction f ON f.id = d.faction_id
GROUP BY f.eid, f.name
ORDER BY wins DESC, matches_played DESC, faction_name ASC
