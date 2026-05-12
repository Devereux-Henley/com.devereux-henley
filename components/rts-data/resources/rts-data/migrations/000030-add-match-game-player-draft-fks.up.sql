-- Per-game draft attachment. Each `match_game` row now references the two
-- player drafts (auto-created from the parsed replay on submit). Drafts
-- become read-only once any match_game references them — see
-- `get-draft-lock-info.sql`.
ALTER TABLE match_game ADD COLUMN player_one_draft_id INTEGER REFERENCES draft(id);
--;;
ALTER TABLE match_game ADD COLUMN player_two_draft_id INTEGER REFERENCES draft(id);
