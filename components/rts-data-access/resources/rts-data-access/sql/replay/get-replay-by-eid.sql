SELECT
  id,
  eid,
  match_id_external,
  played_at,
  victory_condition,
  parser_format,
  parsed_json,
  uploader_local_alliance_index,
  uploaded_by_sub,
  created_at,
  updated_at,
  deleted_at
FROM
  replay
WHERE
  eid = ?
  AND deleted_at IS NULL;
