UPDATE match
SET player_two_draft_id = (SELECT id FROM draft WHERE eid = ?),
    updated_at = ?
WHERE eid = ?
