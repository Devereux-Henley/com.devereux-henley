// Touches components/e2e so poly test re-runs the brick on PRs that only
// modify upstream handlers/queries. The long-term fix is rts-9w4 (explicit
// brick deps); until then, every domain-migration PR carries a small
// e2e touch.

const { test, expect } = require('@playwright/test');

const BASE = process.env.RTS_API_BASE_URL || "http://127.0.0.1:3001";
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';

function actionHeaders(user) {
  return {
    Accept: 'application/htmx+html',
    'Content-Type': 'application/json',
    Cookie: `dev_impersonation=${user}`,
  };
}

function apiHeaders(user) {
  return {
    Accept: 'text/html',
    'Content-Type': 'application/json',
    Cookie: `dev_impersonation=${user}`,
  };
}

async function createTournament(request) {
  const eid = crypto.randomUUID();
  const res = await request.put(`${BASE}/actions/tournament/${eid}?version=1`, {
    headers: actionHeaders('dev-admin'),
    data: {
      'game-eid': GAME_EID,
      name: 'Organizer Console E2E Cup',
      description: 'Created by e2e test.',
      timezone: 'UTC',
      'registration-opens-at': '2020-01-01T00:00',
      'registration-closes-at': '2030-01-01T00:00',
    },
  });
  expect(res.status()).toBe(200);
  return eid;
}

async function enter(request, eid, user) {
  await request.post(`${BASE}/actions/tournament/${eid}/entry/me`, {
    headers: actionHeaders(user),
  });
}

async function configureSwissPhase(request, eid) {
  const res = await request.put(`${BASE}/api/tournament-phase-configuration?tournament-eid=${eid}`, {
    headers: apiHeaders('dev-admin'),
    data: {
      phases: [{ 'phase-type': 'swiss', rounds: [{ 'round-index': 0, format: 1 }] }],
    },
  });
  expect(res.status()).toBe(200);
}

async function startTournament(request, eid) {
  await request.post(`${BASE}/actions/tournament/${eid}/start`, {
    headers: actionHeaders('dev-admin'),
  });
}

async function generateRound(request, eid) {
  await request.post(`${BASE}/actions/tournament/${eid}/round`, {
    headers: actionHeaders('dev-admin'),
  });
}

test.describe('Organizer Console', () => {
  test.beforeEach(async ({ context }) => {
    await context.addCookies([
      {
        name: 'dev_impersonation',
        value: 'dev-admin',
        domain: 'localhost',
        path: '/',
        httpOnly: true,
        sameSite: 'Lax',
      },
    ]);
  });

  test('viewer links the organizer to the dedicated console', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    const link = page.locator('.viewer-role-link', { hasText: 'Organizer Console' });
    await expect(link).toBeVisible();
    await expect(link).toHaveAttribute(
      'href',
      `/view/game/${GAME_EID}/tournament/${eid}/organizer.html`,
    );
  });

  test('console page shows the quick-actions shelf, tabs, and danger zone', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/organizer.html`);

    await expect(page).toHaveTitle(/Organizer · Organizer Console E2E Cup/);
    await expect(page.locator('.organizer-only-tag', { hasText: 'Organizer Only' })).toBeVisible();
    await expect(page.locator('.viewer-role-link', { hasText: 'Back to Viewer' })).toBeVisible();

    // Four quick-action cards.
    await expect(page.locator('.org-action-card-title', { hasText: 'Advance Phase' })).toBeVisible();
    await expect(page.locator('.org-action-card-title', { hasText: 'Generate Next Round' })).toBeVisible();
    await expect(page.locator('.org-action-card-title', { hasText: 'Feature a Match' })).toBeVisible();
    await expect(page.locator('.org-action-card-title', { hasText: 'Open Check-in' })).toBeVisible();

    // Management tabs.
    await expect(page.locator('[role="tab"]', { hasText: 'Phases' })).toBeVisible();
    await expect(page.locator('[role="tab"]', { hasText: 'Entrants' })).toBeVisible();
    await expect(page.locator('[role="tab"]', { hasText: 'Disputes' })).toBeVisible();
    await expect(page.locator('[role="tab"]', { hasText: 'Settings' })).toBeVisible();
  });

  test('entrants tab lists registered players', async ({ page, request }) => {
    const eid = await createTournament(request);
    await enter(request, eid, 'dev-player-one');
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/organizer.html`);

    await page.locator('#org-tab-entrants').click();
    await expect(
      page.locator('#org-panel-entrants table.standings-table tbody tr', { hasText: 'dev-player-one' }),
    ).toBeVisible();
  });

  test('advance phase is gated until the current round fully reports', async ({ page, request }) => {
    const eid = await createTournament(request);
    await configureSwissPhase(request, eid);
    await enter(request, eid, 'dev-admin');
    await enter(request, eid, 'dev-player-one');
    await startTournament(request, eid);
    await generateRound(request, eid);

    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/organizer.html`);

    // Round in progress (results unreported) → advance disabled, generate enabled.
    await expect(page.locator('button', { hasText: 'Advance Phase' })).toBeDisabled();
    await expect(page.locator('button', { hasText: 'Generate Round' })).toBeEnabled();
  });

  test('non-organizers cannot reach the console', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.get(`${BASE}/view/game/${GAME_EID}/tournament/${eid}/organizer.html`, {
      headers: actionHeaders('dev-player-one'),
    });
    expect(res.status()).toBe(404);
  });
});
