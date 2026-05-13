const { test, expect } = require('@playwright/test');

const BASE = process.env.RTS_API_BASE_URL || "http://127.0.0.1:3001";
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';

// JSON-shape API contract tests were dropped along with the JSON formats
// themselves; PR F replaces /api JSON with an HTML hypermedia surface,
// and /actions is the mutation surface. What remains here is the UI
// behavior: pages render, controls appear at the right state-machine
// edges, htmx tabs lazy-load.

function actionHeaders(user) {
  return {
    Accept: 'application/htmx+html',
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
  await request.put(`${BASE}/actions/tournament/${eid}/phase`, {
    headers: actionHeaders('dev-admin'),
    data: {
      phases: [{ 'phase-type': 'swiss', rounds: [{ 'round-index': 0, format: 1 }] }],
    },
  });
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

  test('tournament detail page shows entries section', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page).toHaveTitle(/E2E Test Tournament/);
    await expect(page.locator('h3', { hasText: 'Entries' })).toBeVisible();
    await expect(page.locator('button', { hasText: 'Enter' })).toBeVisible();
  });

  test('tournament detail shows withdraw button after entering', async ({ page, request }) => {
    const eid = await createTournament(request);
    await enter(request, eid, 'dev-admin');
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page.locator('button', { hasText: 'Withdraw' })).toBeVisible();
    await expect(page.locator('li', { hasText: 'dev-admin' })).toBeVisible();
  });

  test('organizer sees Start Tournament button', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page.locator('button', { hasText: 'Start Tournament' })).toBeVisible();
    await expect(page.locator('button', { hasText: 'Close Registration' })).toBeVisible();
  });
});

test.describe('Tournament Detail UI — Tabs', () => {
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

  test('entrants tab is selected on initial load', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page.locator('#tab-entrants')).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#panel-entrants')).toBeVisible();
  });

  test('clicking a phase tab lazy-loads the phase panel via htmx', async ({ page, request }) => {
    const eid = await createTournament(request);
    await configureSwissPhase(request, eid);
    await enter(request, eid, 'dev-admin');
    await enter(request, eid, 'dev-player-one');
    await startTournament(request, eid);
    await generateRound(request, eid);

    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page.locator('#panel-phase-0')).toBeHidden();

    await page.locator('#tab-phase-0').click();

    await expect(page.locator('#tab-phase-0')).toHaveAttribute('aria-selected', 'true');
    await expect(page.locator('#panel-phase-0')).toBeVisible();
    await expect(page.locator('#panel-phase-0 table')).toBeVisible();
    await expect(page.locator('#panel-phase-0 .tourney-match-list')).toBeVisible();
    await expect(page.locator('#tab-entrants')).toHaveAttribute('aria-selected', 'false');
    await expect(page.locator('#panel-entrants')).toBeHidden();
  });
});
