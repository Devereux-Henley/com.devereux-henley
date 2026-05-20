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
      name: 'E2E Test Tournament',
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

async function startTournament(request, eid) {
  await request.post(`${BASE}/actions/tournament/${eid}/start`, {
    headers: actionHeaders('dev-admin'),
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

async function configureSinglePhase(request, eid) {
  const res = await request.put(`${BASE}/api/tournament-phase-configuration?tournament-eid=${eid}`, {
    headers: apiHeaders('dev-admin'),
    data: {
      phases: [{ 'phase-type': 'single-elimination', rounds: [{ 'round-index': 0, format: 1 }] }],
      'qualifier-count': 2,
    },
  });
  expect(res.status()).toBe(200);
}

async function generateRound(request, eid) {
  await request.post(`${BASE}/actions/tournament/${eid}/round`, {
    headers: actionHeaders('dev-admin'),
  });
}

test.describe('Tournament UI', () => {
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

  test('competitive landing replaces the old tournament list', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/competitive/index.html`);
    await expect(page).toHaveTitle(/Competitive/);
    await expect(page.locator('main#content')).toBeVisible();
    await expect(page.locator('[role="tab"]', { hasText: 'Tournaments' })).toBeVisible();
    await expect(page.locator('[role="tab"]', { hasText: 'Leagues' })).toBeVisible();
  });

  test('create tournament page loads with form', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/tournament/create.html`);
    await expect(page).toHaveTitle(/Create Tournament/);
    await expect(page.locator('#tournament-name')).toBeVisible();
    await expect(page.locator('#timezone')).toBeVisible();
    await expect(page.locator('#registration-opens-at')).toBeVisible();
    await expect(page.locator('#registration-closes-at')).toBeVisible();
  });

  test('tournament detail page shows standings + entry action', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page).toHaveTitle(/E2E Test Tournament/);
    await expect(page.locator('h3', { hasText: 'Standings' })).toBeVisible();
    await expect(page.locator('button', { hasText: 'Enter Tournament' })).toBeVisible();
  });

  test('tournament detail shows withdraw button after entering', async ({ page, request }) => {
    const eid = await createTournament(request);
    await enter(request, eid, 'dev-admin');
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page.locator('button', { hasText: 'Withdraw' })).toBeVisible();
    await expect(page.locator('table.standings-table tbody tr', { hasText: 'dev-admin' })).toBeVisible();
  });
});

test.describe('Tournament Viewer page', () => {
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

  test('viewer hero shows status badge and title', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page.locator('.viewer-hero-title', { hasText: 'E2E Test Tournament' })).toBeVisible();
    await expect(page.locator('.viewer-hero-status-live')).toBeVisible();
  });

  test('after start + round generation, bracket section renders matches and schedule lists pending', async ({ page, request }) => {
    const eid = await createTournament(request);
    await configureSinglePhase(request, eid);
    await enter(request, eid, 'dev-admin');
    await enter(request, eid, 'dev-player-one');
    await startTournament(request, eid);
    await generateRound(request, eid);

    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);

    await expect(page.locator('h3', { hasText: 'Bracket' })).toBeVisible();
    await expect(page.locator('.bracket-col-label')).toHaveText('Final');
    await expect(page.locator('.bracket-slot', { hasText: 'dev-admin' })).toBeVisible();

    await expect(page.locator('h3', { hasText: 'Schedule' })).toBeVisible();
    await expect(page.locator('.schedule-row.schedule-row--next')).toBeVisible();
  });
});
