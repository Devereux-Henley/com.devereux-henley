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

test.describe('Tournament Entry API', () => {
  test('create entry returns 201', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.type).toBe('tournament/entry');
    expect(body['player-sub']).toBe('dev-admin');
    expect(body['tournament-eid']).toBe(eid);
  });

  test('duplicate entry returns 422', async ({ request }) => {
    const eid = await createTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-admin'),
    });
    const res = await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(422);
    const body = await res.json();
    expect(body.type).toBe('tournament/entry-error');
    expect(body.message).toMatch(/already entered/i);
  });

  test('multiple players can enter', async ({ request }) => {
    const eid = await createTournament(request);
    const r1 = await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-admin'),
    });
    const r2 = await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-player-one'),
    });
    expect(r1.status()).toBe(201);
    expect(r2.status()).toBe(201);

    const res = await request.get(`${BASE}/api/tournament/${eid}/entry`, {
      headers: headers('dev-admin'),
    });
    const body = await res.json();
    expect(body.entries).toHaveLength(2);
  });

  test('delete entry returns 200', async ({ request }) => {
    const eid = await createTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-player-one'),
    });
    const res = await request.delete(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-player-one'),
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.type).toBe('tournament/entry-deleted');
  });

  test('deleted entry does not appear in entries list', async ({ request }) => {
    const eid = await createTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-admin'),
    });
    await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-player-one'),
    });
    await request.delete(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-player-one'),
    });

    const res = await request.get(`${BASE}/api/tournament/${eid}/entry`, {
      headers: headers('dev-admin'),
    });
    const body = await res.json();
    expect(body.entries).toHaveLength(1);
    expect(body.entries[0]['player-sub']).toBe('dev-admin');
  });

  test('entries list is empty for new tournament', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.get(`${BASE}/api/tournament/${eid}/entry`, {
      headers: headers('dev-admin'),
    });
    const body = await res.json();
    expect(body.entries).toHaveLength(0);
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

  test('tournament detail page shows entries section', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/index.html`);
    await expect(page).toHaveTitle(/E2E Test Tournament/);
    await expect(page.locator('h3', { hasText: 'Entries' })).toBeVisible();
    await expect(page.locator('button', { hasText: 'Enter' })).toBeVisible();
  });

  test('tournament detail shows withdraw button after entering', async ({ page, request }) => {
    const eid = await createTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-admin'),
    });
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

test.describe('Tournament Status Subresource API', () => {
  test('get status returns current status and available transitions', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.get(`${BASE}/api/tournament/${eid}/status`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.type).toBe('tournament/status');
    expect(body.status).toBe('registration');
    expect(body['available-transitions']).toContain('active');
    expect(body['available-transitions']).toContain('cancelled');
  });

  test('put status to active populates standings', async ({ request }) => {
    const eid = await createTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-admin'),
    });
    await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-player-one'),
    });

    const res = await request.put(`${BASE}/api/tournament/${eid}/status`, {
      headers: headers('dev-admin'),
      data: { status: 'active' },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.type).toBe('tournament/advance-success');
    expect(body.state.status).toBe('active');
    expect(body.state.standings).toHaveLength(2);
  });

  test('put status from active to complete', async ({ request }) => {
    const eid = await createTournament(request);
    await request.put(`${BASE}/api/tournament/${eid}/status`, {
      headers: headers('dev-admin'),
      data: { status: 'active' },
    });
    const res = await request.put(`${BASE}/api/tournament/${eid}/status`, {
      headers: headers('dev-admin'),
      data: { status: 'complete' },
    });
    expect(res.status()).toBe(200);
    expect((await res.json()).state.status).toBe('complete');
  });

  test('invalid transition returns 422', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.put(`${BASE}/api/tournament/${eid}/status`, {
      headers: headers('dev-admin'),
      data: { status: 'complete' },
    });
    expect(res.status()).toBe(422);
    expect((await res.json()).type).toBe('tournament/transition-error');
  });

  test('non-organizer cannot update status', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.put(`${BASE}/api/tournament/${eid}/status`, {
      headers: headers('dev-player-one'),
      data: { status: 'active' },
    });
    expect(res.status()).toBe(422);
    expect((await res.json()).message).toMatch(/organizer/i);
  });

  test('cancel tournament from registration', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.put(`${BASE}/api/tournament/${eid}/status`, {
      headers: headers('dev-admin'),
      data: { status: 'cancelled' },
    });
    expect(res.status()).toBe(200);
    expect((await res.json()).state.status).toBe('cancelled');
  });
});

test.describe('Tournament Registration Subresource API', () => {
  test('get registration returns window details', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.get(`${BASE}/api/tournament/${eid}/registration`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.type).toBe('tournament/registration');
    expect(body['opens-at']).toBeDefined();
    expect(body['closes-at']).toBeDefined();
    expect(body['closed-early']).toBe(false);
  });

  test('patch registration closed-early prevents new entries', async ({ request }) => {
    const eid = await createTournament(request);
    const patchRes = await request.patch(`${BASE}/api/tournament/${eid}/registration`, {
      headers: headers('dev-admin'),
      data: { 'closed-early': true },
    });
    expect(patchRes.status()).toBe(200);
    expect((await patchRes.json()).type).toBe('tournament/close-registration-success');

    const entryRes = await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
      headers: headers('dev-player-one'),
    });
    expect(entryRes.status()).toBe(422);
    expect((await entryRes.json()).message).toMatch(/not open/i);
  });
});

async function createActiveTournament(request) {
  const eid = await createTournament(request);
  await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
    headers: headers('dev-admin'),
  });
  await request.post(`${BASE}/api/tournament/${eid}/entry/me`, {
    headers: headers('dev-player-one'),
  });
  await request.put(`${BASE}/api/tournament/${eid}/status`, {
    headers: headers('dev-admin'),
    data: { status: 'active' },
  });
  return eid;
}

test.describe('Tournament Match API', () => {
  test('create match returns 201 when tournament is active', async ({ request }) => {
    const eid = await createActiveTournament(request);
    const res = await request.post(`${BASE}/api/tournament/${eid}/match`, {
      headers: headers('dev-admin'),
      data: {
        'phase-index': 0,
        'round-index': 0,
        'player-one-sub': 'dev-admin',
        'player-two-sub': 'dev-player-one',
      },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.type).toBe('tournament/match');
    expect(body['player-one-sub']).toBe('dev-admin');
    expect(body['player-two-sub']).toBe('dev-player-one');
    expect(body.status).toBe('pending');
  });

  test('create match rejects when tournament not active', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.post(`${BASE}/api/tournament/${eid}/match`, {
      headers: headers('dev-admin'),
      data: {
        'phase-index': 0,
        'round-index': 0,
        'player-one-sub': 'dev-admin',
        'player-two-sub': 'dev-player-one',
      },
    });
    expect(res.status()).toBe(422);
    expect((await res.json()).type).toBe('tournament/match-error');
  });

  test('list matches for tournament', async ({ request }) => {
    const eid = await createActiveTournament(request);
    await request.post(`${BASE}/api/tournament/${eid}/match`, {
      headers: headers('dev-admin'),
      data: { 'phase-index': 0, 'round-index': 0, 'player-one-sub': 'dev-admin', 'player-two-sub': 'dev-player-one' },
    });
    const res = await request.get(`${BASE}/api/tournament/${eid}/match`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.matches).toHaveLength(1);
  });

  test('record match result updates standings', async ({ request }) => {
    const eid = await createActiveTournament(request);
    const createRes = await request.post(`${BASE}/api/tournament/${eid}/match`, {
      headers: headers('dev-admin'),
      data: { 'phase-index': 0, 'round-index': 0, 'player-one-sub': 'dev-admin', 'player-two-sub': 'dev-player-one' },
    });
    const matchEid = (await createRes.json()).eid;

    const res = await request.put(`${BASE}/api/tournament/${eid}/match/${matchEid}/result`, {
      headers: headers('dev-admin'),
      data: { 'winner-sub': 'dev-admin' },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.type).toBe('tournament/match-result-recorded');

    const winner = body.standings.find(s => s['player-sub'] === 'dev-admin');
    expect(winner.wins).toBe(1);
    expect(winner.points).toBe(3);

    const loser = body.standings.find(s => s['player-sub'] === 'dev-player-one');
    expect(loser.losses).toBe(1);
  });

  test('record result on non-pending match returns 422', async ({ request }) => {
    const eid = await createActiveTournament(request);
    const createRes = await request.post(`${BASE}/api/tournament/${eid}/match`, {
      headers: headers('dev-admin'),
      data: { 'phase-index': 0, 'round-index': 0, 'player-one-sub': 'dev-admin', 'player-two-sub': 'dev-player-one' },
    });
    const matchEid = (await createRes.json()).eid;

    await request.put(`${BASE}/api/tournament/${eid}/match/${matchEid}/result`, {
      headers: headers('dev-admin'),
      data: { 'winner-sub': 'dev-admin' },
    });
    const res = await request.put(`${BASE}/api/tournament/${eid}/match/${matchEid}/result`, {
      headers: headers('dev-admin'),
      data: { 'winner-sub': 'dev-admin' },
    });
    expect(res.status()).toBe(422);
    expect((await res.json()).type).toBe('tournament/match-error');
  });
});

async function createTournamentWithPhases(request) {
  const eid = await createTournament(request);
  // Configure Swiss phase with 2 rounds (bo1) + elimination with 1 round (bo3)
  await request.put(`${BASE}/api/tournament/${eid}/phase`, {
    headers: headers('dev-admin'),
    data: {
      phases: [
        { 'phase-type': 'swiss', rounds: [{ 'round-index': 0, format: 1 }, { 'round-index': 1, format: 1 }] },
        { 'phase-type': 'single-elimination', rounds: [{ 'round-index': 0, format: 3 }] },
      ],
      'qualifier-count': 2,
    },
  });
  // Add 4 entries
  await request.post(`${BASE}/api/tournament/${eid}/entry/me`, { headers: headers('dev-admin') });
  await request.post(`${BASE}/api/tournament/${eid}/entry/me`, { headers: headers('dev-player-one') });
  await request.post(`${BASE}/api/tournament/${eid}/entry/me`, { headers: headers('dev-player-two') });
  // Activate
  await request.put(`${BASE}/api/tournament/${eid}/status`, {
    headers: headers('dev-admin'),
    data: { status: 'active' },
  });
  return eid;
}

test.describe('Tournament Phase & Swiss API', () => {
  test('configure phases during registration', async ({ request }) => {
    const eid = await createTournament(request);
    const res = await request.put(`${BASE}/api/tournament/${eid}/phase`, {
      headers: headers('dev-admin'),
      data: {
        phases: [{ 'phase-type': 'swiss', rounds: [{ 'round-index': 0, format: 1 }] }],
        'qualifier-count': 2,
      },
    });
    expect(res.status()).toBe(200);
    expect((await res.json()).type).toBe('tournament/phase-configured');
  });

  test('generate first Swiss round', async ({ request }) => {
    const eid = await createTournamentWithPhases(request);
    const res = await request.post(`${BASE}/api/tournament/${eid}/round/generate`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.type).toBe('tournament/round-generated');
    expect(body.round).toBe(0);
    expect(body.matches.length).toBeGreaterThan(0);
  });

  test('record game result completes bo1 match', async ({ request }) => {
    const eid = await createTournamentWithPhases(request);
    // Generate round
    const roundRes = await request.post(`${BASE}/api/tournament/${eid}/round/generate`, {
      headers: headers('dev-admin'),
    });
    const matches = (await roundRes.json()).matches;
    const match = matches.find(m => m['player-two-sub'] != null);

    const gameRes = await request.post(`${BASE}/api/tournament/${eid}/match/${match.eid}/game`, {
      headers: headers('dev-admin'),
      data: { 'winner-sub': match['player-one-sub'] },
    });
    expect(gameRes.status()).toBe(200);
    const body = await gameRes.json();
    // bo1: one game should complete the match
    expect(body.type).toBe('tournament/match-completed');
    expect(body['winner-sub']).toBe(match['player-one-sub']);
  });

  test('bo3 match requires 2 wins', async ({ request }) => {
    const eid = await createTournament(request);
    // Configure with bo3
    await request.put(`${BASE}/api/tournament/${eid}/phase`, {
      headers: headers('dev-admin'),
      data: {
        phases: [{ 'phase-type': 'swiss', rounds: [{ 'round-index': 0, format: 3 }] }],
      },
    });
    await request.post(`${BASE}/api/tournament/${eid}/entry/me`, { headers: headers('dev-admin') });
    await request.post(`${BASE}/api/tournament/${eid}/entry/me`, { headers: headers('dev-player-one') });
    await request.put(`${BASE}/api/tournament/${eid}/status`, {
      headers: headers('dev-admin'),
      data: { status: 'active' },
    });
    const roundRes = await request.post(`${BASE}/api/tournament/${eid}/round/generate`, {
      headers: headers('dev-admin'),
    });
    const matches = (await roundRes.json()).matches;
    const match = matches.find(m => m['player-two-sub'] != null);
    const p1 = match['player-one-sub'];
    const p2 = match['player-two-sub'];

    // Game 1: p1 wins — match still in progress
    const g1 = await request.post(`${BASE}/api/tournament/${eid}/match/${match.eid}/game`, {
      headers: headers('dev-admin'),
      data: { 'winner-sub': p1 },
    });
    expect((await g1.json()).type).toBe('tournament/game-recorded');

    // Game 2: p2 wins — still in progress
    const g2 = await request.post(`${BASE}/api/tournament/${eid}/match/${match.eid}/game`, {
      headers: headers('dev-admin'),
      data: { 'winner-sub': p2 },
    });
    expect((await g2.json()).type).toBe('tournament/game-recorded');

    // Game 3: p1 wins — match complete (2 wins)
    const g3 = await request.post(`${BASE}/api/tournament/${eid}/match/${match.eid}/game`, {
      headers: headers('dev-admin'),
      data: { 'winner-sub': p1 },
    });
    const body = await g3.json();
    expect(body.type).toBe('tournament/match-completed');
    expect(body['winner-sub']).toBe(p1);
  });
});

test.describe('Tournament Phase Advancement API', () => {
  test('advance to next phase after Swiss', async ({ request }) => {
    const eid = await createTournamentWithPhases(request);
    // Generate and complete Swiss round 0
    const r0 = await request.post(`${BASE}/api/tournament/${eid}/round/generate`, {
      headers: headers('dev-admin'),
    });
    const r0matches = (await r0.json()).matches;
    for (const m of r0matches) {
      if (m['player-two-sub']) {
        await request.post(`${BASE}/api/tournament/${eid}/match/${m.eid}/game`, {
          headers: headers('dev-admin'),
          data: { 'winner-sub': m['player-one-sub'] },
        });
      }
    }

    // Start elimination
    const elimRes = await request.post(`${BASE}/api/tournament/${eid}/phase/advance`, {
      headers: headers('dev-admin'),
    });
    expect(elimRes.status()).toBe(200);
    const body = await elimRes.json();
    expect(body.type).toBe('tournament/phase-advanced');
    expect(body.matches.length).toBeGreaterThan(0);
  });
});
