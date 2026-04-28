-- Hand-written for now (re-runnable via this seed file).  The values are
-- the slug-style race keys used as the join target for the scraper-emitted
-- `seed-subfactions.sql`.  Single CASE-based UPDATE so next.jdbc.execute!
-- (which only runs the first statement of a multi-statement string)
-- applies every assignment in one call.
UPDATE faction
SET key = CASE eid
  WHEN '35dd38fa-2bcc-4492-8f58-a106d0d02cbb' THEN 'empire'
  WHEN 'f0000002-0000-4000-8000-000000000000' THEN 'beastmen'
  WHEN 'f0000003-0000-4000-8000-000000000000' THEN 'bretonnia'
  WHEN 'f0000004-0000-4000-8000-000000000000' THEN 'chaos-dwarfs'
  WHEN 'f0000005-0000-4000-8000-000000000000' THEN 'daemons-of-chaos'
  WHEN 'f0000006-0000-4000-8000-000000000000' THEN 'dark-elves'
  WHEN 'f0000007-0000-4000-8000-000000000000' THEN 'dwarfs'
  WHEN 'f0000008-0000-4000-8000-000000000000' THEN 'grand-cathay'
  WHEN 'f0000009-0000-4000-8000-000000000000' THEN 'greenskins'
  WHEN 'f000000a-0000-4000-8000-000000000000' THEN 'high-elves'
  WHEN 'f000000b-0000-4000-8000-000000000000' THEN 'khorne'
  WHEN 'f000000c-0000-4000-8000-000000000000' THEN 'kislev'
  WHEN 'f000000d-0000-4000-8000-000000000000' THEN 'lizardmen'
  WHEN 'f000000e-0000-4000-8000-000000000000' THEN 'norsca'
  WHEN 'f000000f-0000-4000-8000-000000000000' THEN 'nurgle'
  WHEN 'f0000010-0000-4000-8000-000000000000' THEN 'ogre-kingdoms'
  WHEN 'f0000011-0000-4000-8000-000000000000' THEN 'skaven'
  WHEN 'f0000012-0000-4000-8000-000000000000' THEN 'slaanesh'
  WHEN 'f0000013-0000-4000-8000-000000000000' THEN 'tomb-kings'
  WHEN 'f0000014-0000-4000-8000-000000000000' THEN 'tzeentch'
  WHEN 'f0000015-0000-4000-8000-000000000000' THEN 'vampire-coast'
  WHEN 'f0000016-0000-4000-8000-000000000000' THEN 'vampire-counts'
  WHEN 'f0000017-0000-4000-8000-000000000000' THEN 'warriors-of-chaos'
  WHEN 'f0000018-0000-4000-8000-000000000000' THEN 'wood-elves'
  ELSE key
END;
