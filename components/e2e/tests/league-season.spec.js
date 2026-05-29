const { test, expect } = require('@playwright/test');

const BASE = process.env.RTS_API_BASE_URL || 'http://127.0.0.1:3001';
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';

function actionHeaders(user) {
  return {
    Accept: 'application/htmx+html',
    'Content-Type': 'application/json',
    Cookie: `dev_impersonation=${user}`,
  };
}

async function createLeague(request, { name, description, user = 'dev-admin' } = {}) {
  const eid = crypto.randomUUID();
  const res = await request.put(`${BASE}/actions/league/${eid}?version=1`, {
    headers: actionHeaders(user),
    data: {
      'game-eid': GAME_EID,
      name: name || `E2E League ${eid.slice(0, 8)}`,
      description: description || 'Created by e2e test.',
    },
  });
  expect(res.status()).toBe(200);
  return eid;
}

async function createSeason(request, leagueEid, { name, startAt, endAt, user = 'dev-admin' } = {}) {
  const eid = crypto.randomUUID();
  const body = {
    timezone: 'UTC',
    'start-at': startAt || '2026-04-01T00:00',
    'end-at':   endAt   || '2030-06-30T23:59',
  };
  if (name) body.name = name;
  const res = await request.put(`${BASE}/actions/season/${leagueEid}/${eid}`, {
    headers: actionHeaders(user),
    data: body,
  });
  expect(res.status()).toBe(200);
  return eid;
}

test.describe('League + season UI', () => {
  test.beforeEach(async ({ context }) => {
    await context.addCookies([
      {
        name: 'dev_impersonation',
        value: 'dev-admin',
        domain: '127.0.0.1',
        path: '/',
        httpOnly: true,
        sameSite: 'Lax',
      },
    ]);
  });

  test('create-league form loads with required fields', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/league/create.html`);
    await expect(page).toHaveTitle(/Create League/);
    await expect(page.locator('#league-name')).toBeVisible();
    await expect(page.locator('#league-description')).toBeVisible();
    await expect(page.locator('button[type=submit]', { hasText: 'Create League' })).toBeVisible();
  });

  test('league detail page renders heading, description, and seasons empty state', async ({ page, request }) => {
    const leagueEid = await createLeague(request, { name: 'Spring Showdown', description: 'A test league' });
    await page.goto(`/view/game/${GAME_EID}/league/${leagueEid}/index.html`);

    await expect(page).toHaveTitle(/Spring Showdown/);
    await expect(page.locator('#league-heading')).toContainText('Spring Showdown');
    await expect(page.locator('.resource-description')).toContainText('A test league');
    await expect(page.locator('.resource-meta')).toContainText('Organized by dev-admin');
    await expect(page.locator('#league-seasons-heading + p')).toContainText('No seasons yet');
  });

  test('organizer sees Create Season link on owned league', async ({ page, request }) => {
    const leagueEid = await createLeague(request);
    await page.goto(`/view/game/${GAME_EID}/league/${leagueEid}/index.html`);
    await expect(page.locator(`a[href*="/league/${leagueEid}/season/create.html"]`)).toBeVisible();
  });

  test('non-organizer does not see Create Season link', async ({ browser, request }) => {
    const leagueEid = await createLeague(request, { user: 'dev-admin' });
    const ctx  = await browser.newContext();
    await ctx.addCookies([
      { name: 'dev_impersonation', value: 'dev-player-one', domain: '127.0.0.1', path: '/', sameSite: 'Lax' },
    ]);
    const page = await ctx.newPage();
    await page.goto(`${BASE}/view/game/${GAME_EID}/league/${leagueEid}/index.html`);
    await expect(page.locator('#league-heading')).toBeVisible();
    await expect(page.locator(`a[href*="/league/${leagueEid}/season/create.html"]`)).toHaveCount(0);
    await ctx.close();
  });

  test('create-season form loads with required fields', async ({ page, request }) => {
    const leagueEid = await createLeague(request);
    await page.goto(`/view/game/${GAME_EID}/league/${leagueEid}/season/create.html`);
    await expect(page).toHaveTitle(/Create Season/);
    await expect(page.locator('#season-name')).toBeVisible();
    await expect(page.locator('#timezone')).toBeVisible();
    await expect(page.locator('#start-at')).toBeVisible();
    await expect(page.locator('#end-at')).toBeVisible();
  });

  test('season detail page defaults display-name to "Season N" when no custom name', async ({ page, request }) => {
    const leagueEid = await createLeague(request, { name: 'Default Names League' });
    const seasonEid = await createSeason(request, leagueEid);
    await page.goto(`/view/game/${GAME_EID}/league/${leagueEid}/season/${seasonEid}/index.html`);

    await expect(page).toHaveTitle(/Season 1/);
    await expect(page.locator('#season-heading')).toHaveText('Season 1');
    // Parent league link points back to the league.
    await expect(page.locator('.resource-meta a', { hasText: 'Default Names League' }))
      .toHaveAttribute('href', `/view/game/${GAME_EID}/league/${leagueEid}/index.html`);
  });

  test('season detail page honours a custom name override', async ({ page, request }) => {
    const leagueEid = await createLeague(request);
    const seasonEid = await createSeason(request, leagueEid, { name: 'Spring 2026' });
    await page.goto(`/view/game/${GAME_EID}/league/${leagueEid}/season/${seasonEid}/index.html`);

    await expect(page).toHaveTitle(/Spring 2026/);
    await expect(page.locator('#season-heading')).toHaveText('Spring 2026');
  });

  test('league seasons section lists created seasons in ordinal order', async ({ page, request }) => {
    const leagueEid = await createLeague(request, { name: 'Multi-Season' });
    await createSeason(request, leagueEid);
    await createSeason(request, leagueEid, { name: 'Summer 2026' });

    await page.goto(`/view/game/${GAME_EID}/league/${leagueEid}/index.html`);
    const items = page.locator('.season-list li a');
    await expect(items).toHaveCount(2);
    await expect(items.nth(0)).toHaveText(/Season 1/);
    await expect(items.nth(1)).toHaveText(/Summer 2026/);
  });

  test('full league → season hypermedia walk', async ({ page, request }) => {
    // Exercises the migration end-to-end: create-league action → league page →
    // create-season action → season page → back to league page (now non-empty).
    const leagueEid = await createLeague(request, { name: 'Walkthrough League' });
    const seasonEid = await createSeason(request, leagueEid);

    await page.goto(`/view/game/${GAME_EID}/league/${leagueEid}/index.html`);
    await page.locator('.season-list li a', { hasText: 'Season 1' }).click();

    await expect(page).toHaveTitle(/Season 1/);
    await expect(page.locator('#season-heading')).toHaveText('Season 1');

    await page.locator('.resource-meta a', { hasText: 'Walkthrough League' }).click();
    await expect(page).toHaveTitle(/Walkthrough League/);
    await expect(page.locator('.season-list li a', { hasText: 'Season 1' }))
      .toHaveAttribute('href', `/view/game/${GAME_EID}/league/${leagueEid}/season/${seasonEid}/index.html`);
  });
});
