// Touches components/e2e so poly test re-runs the brick on PRs that only
// modify upstream handlers/queries (rts-9w4 workaround).
//
// End-to-end organizer walkthrough exercising the colocated/datalog view
// reads (rts-8g0/j5m): create → configure phase → register → start →
// generate round → record a result → confirm the detail page renders the
// bracket (per-match game counts via the nested matches+games pull) and
// the recomputed standings.

const { test, expect } = require('@playwright/test');

const BASE = process.env.RTS_API_BASE_URL || 'http://127.0.0.1:3001';
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';

function actionHeaders(user) {
  return { Accept: 'application/htmx+html', 'Content-Type': 'application/json',
           Cookie: `dev_impersonation=${user}` };
}
function htmlHeaders(user) { return { Accept: 'text/html', Cookie: `dev_impersonation=${user}` }; }

async function setupFinalMatch(request) {
  const eid = crypto.randomUUID();
  expect((await request.put(`${BASE}/actions/tournament/${eid}?version=1`, {
    headers: actionHeaders('dev-admin'),
    data: { 'game-eid': GAME_EID, name: 'Walkthrough Cup', description: 'd', timezone: 'UTC',
            'registration-opens-at': '2020-01-01T00:00', 'registration-closes-at': '2030-01-01T00:00' },
  })).status()).toBe(200);
  expect((await request.put(`${BASE}/api/tournament-phase-configuration?tournament-eid=${eid}`, {
    headers: { ...htmlHeaders('dev-admin'), 'Content-Type': 'application/json' },
    data: { phases: [{ 'phase-type': 'single-elimination', rounds: [{ 'round-index': 0, format: 1 }] }],
            'qualifier-count': 2 },
  })).status()).toBe(200);
  await request.post(`${BASE}/actions/tournament/${eid}/entry/me`, { headers: actionHeaders('dev-player-one') });
  await request.post(`${BASE}/actions/tournament/${eid}/entry/me`, { headers: actionHeaders('dev-player-two') });
  await request.post(`${BASE}/actions/tournament/${eid}/start`, { headers: actionHeaders('dev-admin') });
  await request.post(`${BASE}/actions/tournament/${eid}/round`, { headers: actionHeaders('dev-admin') });
  const html = await (await request.get(`${BASE}/api/match?tournament-eid=${eid}`,
                                        { headers: htmlHeaders('dev-admin') })).text();
  const m = html.match(/\/match\/([0-9a-f-]{36})/);
  expect(m, 'a generated match should exist').not.toBeNull();
  return { eid, matchEid: m[1] };
}

test.describe('Tournament walkthrough (colocated views)', () => {
  test('record a result → detail page shows the bracket and recomputed standings', async ({ page, request }) => {
    const { eid, matchEid } = await setupFinalMatch(request);

    // Record the (bo1) result for the final.
    const res = await request.put(`${BASE}/actions/tournament/${eid}/match/${matchEid}/result`, {
      headers: actionHeaders('dev-admin'),
      data: { 'winner-sub': 'dev-player-one' },
    });
    expect(res.status()).toBe(200);

    // The refactored detail view (nested matches+games pull) renders the bracket + standings.
    await page.context().addCookies([{ name: 'dev_impersonation', value: 'dev-admin',
                                       domain: '127.0.0.1', path: '/', sameSite: 'Lax' }]);
    await page.goto(`${BASE}/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page).toHaveTitle(/Walkthrough Cup/);
    await expect(page.locator('h3', { hasText: 'Bracket' })).toBeVisible();
    // Standings recompute from the completed match: winner 1-0, loser 0-1.
    await expect(page.locator('table.standings-table tbody tr', { hasText: 'dev-player-one' }))
      .toContainText('1-0');
    await expect(page.locator('table.standings-table tbody tr', { hasText: 'dev-player-two' }))
      .toContainText('0-1');
  });
});
