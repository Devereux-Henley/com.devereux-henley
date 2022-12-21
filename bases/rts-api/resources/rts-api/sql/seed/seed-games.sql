INSERT OR IGNORE INTO game(eid,
                           name,
                           description,
                           version,
                           created_by_id,
                           created_at,
                           updated_at,
                           deleted_at)
       VALUES
         ('eea787d7-1065-45eb-a3f6-e26f32c294a1',
          'Total War: Warhammer III',
          'The single most poggers warhammer game.',
          1,
          'f0ce7395-a57f-41e9-ade0-fd13bafc058f',
          DATETIME('now'),
          DATETIME('now'),
          null
         );
