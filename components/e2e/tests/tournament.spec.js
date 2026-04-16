const { test, expect } = require('@playwright/test');

const BASE = 'http://127.0.0.1:3001';
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';

function headers(user) {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Cookie: `dev_impersonation=${user}`,
  };
}

async function createTournament(request) {
  const eid = crypto.randomUUID();
  const res = await request.put(`${BASE}/api/tournament/${eid}?version=1`, {
    headers: headers('dev-admin'),
    data: {
      'game-eid': GAME_EID,
      name: 'E2E Test Tournament',
      description: 'Created by e2e test.',
      timezone: 'UTC',
      'registration-opens-at': '2020-01-01T00:00',
      'registration-closes-at': '2030-01-01T00:00',
    },
  });
  expect(res.status()).toBe(201);
  return (await res.json()).eid;
}

test.describe('Tournament API', () => {
  test('create tournament returns tournament resource with _links', async ({ request }) => {
    const eid = crypto.randomUUID();
    const res = await request.put(`${BASE}/api/tournament/${eid}?version=1`, {
      headers: headers('dev-admin'),
      data: {
        'game-eid': GAME_EID,
        name: 'Creation Test',
        description: 'Testing creation.',
        timezone: 'US/Eastern',
        'registration-opens-at': '2026-05-01T00:00',
        'registration-closes-at': '2026-05-15T00:00',
      },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();

    expect(body.type).toBe('tournament/tournament');
    expect(body.eid).toBe(eid);
    expect(body.name).toBe('Creation Test');
    expect(body['created-by-sub']).toBe('dev-admin');
    expect(body._links).toBeDefined();
    expect(body._links.self).toContain(`/api/tournament/${eid}`);
    expect(body._links.game).toContain(`/api/game/${GAME_EID}`);
  });

  test('get tournament by eid', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.get(`${BASE}/api/tournament/${eid}`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.eid).toBe(eid);
    expect(body.type).toBe('tournament/tournament');
  });

  test('get tournament returns 404 for non-existent eid', async ({ request }) => {
    const res = await request.get(`${BASE}/api/tournament/00000000-0000-0000-0000-000000000000`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(404);
  });
});

test.describe('Tournament Registration API', () => {
  test('register player returns 201', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.post(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.type).toBe('tournament/registration');
    expect(body['player-sub']).toBe('dev-admin');
    expect(body['tournament-eid']).toBe(eid);
  });

  test('duplicate registration returns 422', async ({ request }) => {
    const eid = await createTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-admin'),
    });
    const res = await request.post(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(422);
    const body = await res.json();
    expect(body.type).toBe('tournament/registration-error');
    expect(body.message).toMatch(/already registered/i);
  });

  test('multiple players can register', async ({ request }) => {
    const eid = await createTournament(request);
    const r1 = await request.post(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-admin'),
    });
    const r2 = await request.post(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-player-one'),
    });
    expect(r1.status()).toBe(201);
    expect(r2.status()).toBe(201);

    const res = await request.get(`${BASE}/api/tournament/${eid}/registrations`, {
      headers: headers('dev-admin'),
    });
    const body = await res.json();
    expect(body.registrations).toHaveLength(2);
  });

  test('withdraw player returns 200', async ({ request }) => {
    const eid = await createTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-player-one'),
    });
    const res = await request.delete(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-player-one'),
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.type).toBe('tournament/withdraw-success');
  });

  test('withdrawn player does not appear in registrations list', async ({ request }) => {
    const eid = await createTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-admin'),
    });
    await request.post(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-player-one'),
    });
    await request.delete(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-player-one'),
    });

    const res = await request.get(`${BASE}/api/tournament/${eid}/registrations`, {
      headers: headers('dev-admin'),
    });
    const body = await res.json();
    expect(body.registrations).toHaveLength(1);
    expect(body.registrations[0]['player-sub']).toBe('dev-admin');
  });

  test('registrations list is empty for new tournament', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.get(`${BASE}/api/tournament/${eid}/registrations`, {
      headers: headers('dev-admin'),
    });
    const body = await res.json();
    expect(body.registrations).toHaveLength(0);
  });
});

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

  test('tournament list page loads', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/tournament/index.html`);
    await expect(page).toHaveTitle(/Tournaments/);
    await expect(page.locator('main#content')).toBeVisible();
  });

  test('create tournament page loads with form', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/tournament/create.html`);
    await expect(page).toHaveTitle(/Create Tournament/);
    await expect(page.locator('#tournament-name')).toBeVisible();
    await expect(page.locator('#timezone')).toBeVisible();
    await expect(page.locator('#registration-opens-at')).toBeVisible();
    await expect(page.locator('#registration-closes-at')).toBeVisible();
  });

  test('tournament detail page shows registration section', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page).toHaveTitle(/E2E Test Tournament/);
    await expect(page.locator('h3', { hasText: 'Registration' })).toBeVisible();
    await expect(page.locator('button', { hasText: 'Register' })).toBeVisible();
  });

  test('tournament detail shows withdraw button after registration', async ({ page, request }) => {
    const eid = await createTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/register`, {
      headers: headers('dev-admin'),
    });
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page.locator('button', { hasText: 'Withdraw' })).toBeVisible();
    await expect(page.locator('li', { hasText: 'dev-admin' })).toBeVisible();
  });
});
