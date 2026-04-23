UPDATE match
SET player_one_draft_id = (SELECT id FROM draft WHERE eid = ?),
    updated_at = ?
WHERE eid = ?
