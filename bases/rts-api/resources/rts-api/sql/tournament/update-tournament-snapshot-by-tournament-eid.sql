UPDATE tournament_snapshot ts
  SET ts.tournament_state = ?,
      ts.updated_at = ?
JOIN tournament t ON ts.tournament_id = t.id
WHERE t.eid = ?
