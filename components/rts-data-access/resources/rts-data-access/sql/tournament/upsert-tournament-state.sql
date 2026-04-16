INSERT INTO tournament_state (tournament_id, state, updated_at)
VALUES (?, ?, ?)
ON CONFLICT(tournament_id) DO UPDATE SET
  state = excluded.state,
  updated_at = excluded.updated_at
