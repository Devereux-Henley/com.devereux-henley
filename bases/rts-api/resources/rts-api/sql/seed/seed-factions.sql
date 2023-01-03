INSERT OR IGNORE INTO faction(id,
                              eid,
                              game_id,
                              name,
                              description,
                              version,
                              created_by_sub,
                              created_at,
                              updated_at,
                              deleted_at)
VALUES
         (1,
         '35dd38fa-2bcc-4492-8f58-a106d0d02cbb',
         1,
         'Empire',
         'Lying at the heart of the Old World, the Empire is the '
         || 'greatest and most powerful of all the kingdoms forged by '
         || 'men. But it is a realm in constant turmoil, both from enemies '
         || 'without and strife from within as rival Elector Counts vie for '
         || 'the throne. Whether fighting between themselves or the many '
         || 'exterior invaders, the greatest nation of man can muster a '
         || 'formidable army of disciplined warriors, magic, steam-'
         || 'powered wonders and menageries of fantastical beasts.'
         || char(10)
         || char(10)
         || 'Now, the Twin-Tailed Comet blazes across the sky once '
         || 'again and a new Emperor has been crowned - surely an '
         || 'omen that the time has come to unite the Empire, secure its '
         || 'borders and bring prosperity to its beleaguered citizens.',
         1,
         'f0ce7395-a57f-41e9-ade0-fd13bafc058f',
         DATETIME('now'),
         DATETIME('now'),
         null
         );
