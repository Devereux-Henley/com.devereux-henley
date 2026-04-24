SELECT COALESCE(MAX(s.ordinal), 0) AS max_ordinal
FROM season s
  INNER JOIN league l ON l.id = s.league_id
WHERE l.eid = ?
