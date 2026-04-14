INSERT INTO draft_state (draft_id, state, updated_at)
VALUES (?, ?, ?)
ON CONFLICT(draft_id) DO UPDATE SET
  state = excluded.state,
  updated_at = excluded.updated_at
