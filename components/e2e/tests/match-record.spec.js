const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const BASE = process.env.RTS_API_BASE_URL || 'http://127.0.0.1:3001';
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const SAMPLE_REPLAY = path.resolve(__dirname, '../fixtures/sample-battle.replay');

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
      name: 'Match Record E2E',
      description: 'Created for match-record e2e tests.',
      timezone: 'UTC',
      'registration-opens-at': '2020-01-01T00:00',
      'registration-closes-at': '2030-01-01T00:00',
    },
  });
  expect(res.status()).toBe(201);
  return (await res.json()).eid;
}

async function createActiveTournament(request) {
  const eid = await createTournament(request);
  await request.put(`${BASE}/api/tournament/${eid}/status?version=1`, {
    headers: headers('dev-admin'),
    data: { status: 'active' },
  });
  return eid;
}

async function createMatch(request, tournamentEid, format = 3) {
  const res = await request.post(`${BASE}/api/tournament/${tournamentEid}/match`, {
    headers: headers('dev-admin'),
    data: {
      'phase-index': 0,
      'round-index': 0,
      'player-one-sub': 'dev-admin',
      'player-two-sub': 'dev-player-one',
      format,
    },
  });
  expect(res.status()).toBe(201);
  return (await res.json()).eid;
}

async function parseReplays(request, matchEid, count) {
  const buffer = fs.readFileSync(SAMPLE_REPLAY);
  const multipart = {};
  for (let i = 0; i < count; i++) {
    multipart[`game-${i}`] = {
      name: `g${i}.replay`,
      mimeType: 'application/octet-stream',
      buffer,
    };
  }
  const res = await request.post(`${BASE}/api/match/${matchEid}/parse`, {
    headers: { Cookie: 'dev_impersonation=dev-admin' },
    multipart,
  });
  return { status: res.status(), body: await res.json() };
}

async function recordMatch(request, matchEid, games) {
  const res = await request.post(`${BASE}/api/match/${matchEid}/record`, {
    headers: headers('dev-admin'),
    data: { games },
  });
  return { status: res.status(), body: await res.json() };
}

test.describe('Match-record parse endpoint', () => {
  test('returns parsed JSON in series order with snake_case keys', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 3);

    const { status, body } = await parseReplays(request, matchEid, 3);

    expect(status).toBe(200);
    expect(body.type).toBe('match-record/parsed');
    expect(body.games).toHaveLength(3);

    const first = body.games[0];
    expect(first['source-name']).toBe('g0.replay');
    expect(first.parsed.match_id).toBe('7801776992105');
    expect(first.parsed.format).toBe('CBAB');
    expect(first.parsed.alliances).toHaveLength(2);
    expect(first.parsed.alliances[0].faction_key).toBe('wh_main_emp_empire');
  });

  test('rejects empty submission', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 1);

    const res = await request.post(`${BASE}/api/match/${matchEid}/parse`, {
      headers: { Cookie: 'dev_impersonation=dev-admin' },
      multipart: {}, // no files
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.type).toBe('match-record/error');
  });
});

test.describe('Match-record commit endpoint', () => {
  test('records a Bo1 match and completes it', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 1);

    const { body: parsed } = await parseReplays(request, matchEid, 1);
    const games = [{
      'winner-sub': 'dev-admin',
      parsed: parsed.games[0].parsed,
      'source-name': parsed.games[0]['source-name'],
    }];

    const { status, body } = await recordMatch(request, matchEid, games);
    expect(status).toBe(201);
    expect(body.type).toBe('match-record/recorded');
    expect(body['winner-sub']).toBe('dev-admin');
    expect(body['complete?']).toBe(true);
  });

  test('records a Bo3 clinched at 2-0', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 3);

    const { body: parsed } = await parseReplays(request, matchEid, 2);
    const games = parsed.games.map((g) => ({
      'winner-sub': 'dev-admin',
      parsed: g.parsed,
      'source-name': g['source-name'],
    }));

    const { status, body } = await recordMatch(request, matchEid, games);
    expect(status).toBe(201);
    expect(body['winner-sub']).toBe('dev-admin');
  });

  test('rejects undecided submission', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 3);

    // Bo3 with a 1-1 split — undecided.
    const { body: parsed } = await parseReplays(request, matchEid, 2);
    const games = [
      { 'winner-sub': 'dev-admin',      parsed: parsed.games[0].parsed, 'source-name': 'g0.replay' },
      { 'winner-sub': 'dev-player-one', parsed: parsed.games[1].parsed, 'source-name': 'g1.replay' },
    ];

    const { status, body } = await recordMatch(request, matchEid, games);
    expect(status).toBe(422);
    expect(body.message).toMatch(/do not decide the series/);
  });

  test('rejects unknown winner sub', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 1);

    const { body: parsed } = await parseReplays(request, matchEid, 1);
    const games = [{
      'winner-sub': 'someone-else',
      parsed: parsed.games[0].parsed,
      'source-name': 'g0.replay',
    }];

    const { status, body } = await recordMatch(request, matchEid, games);
    expect(status).toBe(422);
    expect(body.message).toMatch(/one of the match's players/);
  });

  test('records winner once and rejects re-submission', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 1);

    const { body: parsed } = await parseReplays(request, matchEid, 1);
    const games = [{
      'winner-sub': 'dev-admin',
      parsed: parsed.games[0].parsed,
      'source-name': 'g0.replay',
    }];

    const first = await recordMatch(request, matchEid, games);
    expect(first.status).toBe(201);

    const second = await recordMatch(request, matchEid, games);
    expect(second.status).toBe(422);
    expect(second.body.message).toMatch(/already complete/);
  });
});

test.describe('Match-record modal view', () => {
  test('returns the modal HTML fragment for a real match', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 3);

    const res = await request.get(`${BASE}/view/match-record/${matchEid}/index.html`, {
      headers: { Accept: 'text/html', Cookie: 'dev_impersonation=dev-admin' },
    });
    expect(res.status()).toBe(200);
    const html = await res.text();
    expect(html).toContain('id="pm-modal-root"');
    expect(html).toContain('data-pm-step="upload"');
    expect(html).toContain(`data-match-eid="${matchEid}"`);
    expect(html).toContain('data-format="3"');
    expect(html).toContain('Record Match');
  });

  test('returns 404 for unknown match-eid', async ({ request }) => {
    const fakeEid = '00000000-0000-4000-8000-000000000000';
    const res = await request.get(`${BASE}/view/match-record/${fakeEid}/index.html`, {
      headers: { Accept: 'text/html', Cookie: 'dev_impersonation=dev-admin' },
    });
    expect(res.status()).toBe(404);
  });
});
