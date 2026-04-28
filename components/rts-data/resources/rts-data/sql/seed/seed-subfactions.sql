-- Initial hand-written seed.  The rpfm-scraper rewrites this file in full
-- when invoked (see bases/rpfm-scraper/.../subfactions_seed.clj); the rows
-- below cover every faction-key the parser emits for the e2e fixture and
-- are sized to keep the post-match modal demoable on a fresh DB.  Names
-- match the in-game / RPFM `factions_screen_name_*` loc strings rather
-- than any informal/legacy spellings.
INSERT OR IGNORE INTO subfaction(id, eid, key, name, faction_id, version,
                                 created_by_sub, created_at, updated_at, deleted_at)
VALUES
  (1, '5f000001-0000-4000-8000-000000000000', 'wh_main_emp_empire',              'Empire',                  1,
   1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (2, '5f000002-0000-4000-8000-000000000000', 'wh_main_emp_reikland',            'Reikland',                1,
   1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (3, '5f000003-0000-4000-8000-000000000000', 'wh3_dlc23_chd_legion_of_azgorh',  'The Legion of Azgorh',    4,
   1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (4, '5f000004-0000-4000-8000-000000000000', 'wh3_dlc23_chd_zharr_naggrund',    'Zharr-Naggrund',          4,
   1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null);
