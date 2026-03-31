SELECT
  gsl.id,
  gsl.eid,
  gsl.url,
  g.eid as game_eid,
  smp.eid as social_media_platform_eid,
  gsl.version,
  gsl.created_at,
  gsl.updated_at,
  gsl.deleted_at
FROM
  game_social_link gsl
INNER JOIN game g ON g.id = gsl.game_id
INNER JOIN social_media_platform smp ON smp.id = gsl.social_media_platform_id
WHERE g.eid = ?
