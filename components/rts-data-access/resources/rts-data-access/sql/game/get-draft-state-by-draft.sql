SELECT
  ds.id,
  ds.state,
  ds.updated_at
FROM
  draft_state ds
  INNER JOIN draft d ON d.id = ds.draft_id
WHERE d.eid = ?
