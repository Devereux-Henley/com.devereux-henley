INSERT OR IGNORE INTO game(id,
                           eid,
                           name,
                           description,
                           version,
                           created_by_sub,
                           created_at,
                           updated_at,
                           deleted_at)
VALUES
         (1,
         'eea787d7-1065-45eb-a3f6-e26f32c294a1',
         'Total War: Warhammer III',
         'The single most poggers warhammer game.',
         1,
         'f0ce7395-a57f-41e9-ade0-fd13bafc058f',
         STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'),
         STRFTIME('%Y-%m-%dT%H:%M:%fZ','now'),
         null
         );
