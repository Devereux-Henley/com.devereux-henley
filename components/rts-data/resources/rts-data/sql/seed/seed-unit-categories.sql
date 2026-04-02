INSERT OR IGNORE INTO unit_category(id,
                                   eid,
                                   name,
                                   description,
                                   game_id,
                                   version,
                                   created_by_sub,
                                   created_at,
                                   updated_at,
                                   deleted_at)
VALUES
  (1, 'a1000000-0000-0000-0000-000000000001', 'Lord',               'Powerful leaders that command your army on the battlefield.',           1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (2, 'a1000000-0000-0000-0000-000000000002', 'Hero',               'Elite characters with unique abilities that support your forces.',      1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (3, 'a1000000-0000-0000-0000-000000000003', 'Melee Infantry',     'Ground troops that engage enemies in close combat.',                    1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (4, 'a1000000-0000-0000-0000-000000000004', 'Missile Infantry',   'Infantry units that attack enemies from range.',                        1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (5, 'a1000000-0000-0000-0000-000000000005', 'Melee Cavalry',      'Mounted warriors that charge and engage enemies in close combat.',      1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (6, 'a1000000-0000-0000-0000-000000000006', 'Missile Cavalry',    'Mounted units that harass enemies with ranged attacks.',                1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (7, 'a1000000-0000-0000-0000-000000000007', 'Chariot',            'Wheeled war vehicles that crash through enemy lines.',                  1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (8, 'a1000000-0000-0000-0000-000000000008', 'War Machine',        'Mechanical constructs and devices built for destruction.',              1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (9, 'a1000000-0000-0000-0000-000000000009', 'Artillery',          'Long-range siege weapons that bombard enemy formations.',               1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (10,'a1000000-0000-0000-0000-000000000010', 'War Beast',          'Tamed creatures of war unleashed upon the enemy.',                     1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (11,'a1000000-0000-0000-0000-000000000011', 'Monstrous Infantry', 'Large humanoid creatures that fight on foot and crush enemy ranks.',    1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null),
  (12,'a1000000-0000-0000-0000-000000000012', 'Monster',            'Enormous creatures that tower over the battlefield and inspire terror.',1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null);

INSERT OR IGNORE INTO unit_category(id, eid, name, description, game_id, version,
                                   created_by_sub, created_at, updated_at, deleted_at)
VALUES
  (13, 'a1000000-0000-0000-0000-000000000013', 'Monstrous Cavalry',
   'Monstrous creatures ridden as cavalry that combine powerful charge with formidable bulk.',
   1, 1, 'f0ce7395-a57f-41e9-ade0-fd13bafc058f', STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'), null);
