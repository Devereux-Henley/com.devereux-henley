const { test, expect } = require('@playwright/test');

const BASE = process.env.RTS_API_BASE_URL || 'http://127.0.0.1:3001';

// Seeded social-media platforms (see components/rts-data/resources/rts-data/seed/datalog/8.0/social-media-platforms.edn).
const TWITCH_EID = '946d4828-2fa1-409b-8c7b-1f84108fcea9';

function headers() {
  return {
    Accept: 'text/html',
    Cookie: 'dev_impersonation=dev-admin',
  };
}

test.describe('Social-media API', () => {
  test('platform endpoint renders the platform name for a known eid', async ({ request }) => {
    const res = await request.get(`${BASE}/api/social-media/${TWITCH_EID}`, { headers: headers() });
    expect(res.status()).toBe(200);
    const body = await res.text();
    expect(body).toContain('Twitch');
    expect(body).toContain('social-media/platform');
  });

  test('platform endpoint returns 404 for an unknown eid', async ({ request }) => {
    const res = await request.get(`${BASE}/api/social-media/00000000-0000-0000-0000-000000000000`, { headers: headers() });
    expect(res.status()).toBe(404);
  });
});
