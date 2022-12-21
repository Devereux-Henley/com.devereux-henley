SELECT
  id,
  eid,
  name,
  description,
  version,
  created_at,
  updated_at,
  deleted_at
FROM
  "Game"
WHERE
  deleted_at IS NULL;
