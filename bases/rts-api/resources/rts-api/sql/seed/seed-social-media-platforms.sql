INSERT OR IGNORE INTO social_media_platform(id,
                                            eid,
                                            name,
                                            description,
                                            platform_url,
                                            version,
                                            created_by_sub,
                                            created_at,
                                            updated_at,
                                            deleted_at)
VALUES
         (1,
         '946d4828-2fa1-409b-8c7b-1f84108fcea9',
         'Twitch',
         'Streaming Platform.mpeg',
         'twitch.tv',
         1,
         'f0ce7395-a57f-41e9-ade0-fd13bafc058f',
         DATETIME('now'),
         DATETIME('now'),
         null
         ),
         (2,
         '41780b82-9fcf-44a4-aa1c-6a57cc835585',
         'YouTube',
         'Video Website.wav',
         'youtube.com',
         1,
         'f0ce7395-a57f-41e9-ade0-fd13bafc058f',
         DATETIME('now'),
         DATETIME('now'),
         null
         );
