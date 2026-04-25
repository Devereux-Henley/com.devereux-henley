SELECT
  id,
  eid,
  match_id,
  played_at,
  victory_condition,
  parser_format,
  parsed_json,
  winning_alliance_idx,
  uploaded_by_sub,
  version,
  created_at,
  updated_at,
  deleted_at
FROM
  replay
WHERE
  uploaded_by_sub = ?
  AND deleted_at IS NULL
ORDER BY
  played_at DESC;
