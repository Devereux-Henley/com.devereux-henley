-- The unique constraint UNIQUE(tournament_id, player_sub) on tournament_entry
-- doesn't carve out soft-deleted rows, so a user who entered and then withdrew
-- could not re-enter the same tournament. Drop the soft-delete pattern: purge
-- existing soft-deleted rows here, and the delete-entry.sql query is updated
-- to hard-delete from now on.
DELETE FROM tournament_entry WHERE deleted_at IS NOT NULL;
