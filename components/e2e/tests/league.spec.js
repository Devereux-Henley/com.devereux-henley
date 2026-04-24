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

async function createLeague(request) {
  const eid = crypto.randomUUID();
  const res = await request.put(`${BASE}/api/league/${eid}?version=1`, {
    headers: headers('dev-admin'),
    data: {
      'game-eid': GAME_EID,
      name: `E2E League ${eid.slice(0, 8)}`,
      description: 'E2E test league',
    },
  });
  expect(res.status()).toBe(201);
  return (await res.json()).eid;
}

async function createSeason(request, leagueEid, opts = {}) {
  const eid = crypto.randomUUID();
  const res = await request.put(`${BASE}/api/season/${leagueEid}/${eid}`, {
    headers: headers('dev-admin'),
    data: {
      timezone: 'UTC',
      'start-at': opts.startAt || '2026-04-01T00:00',
      'end-at': opts.endAt || '2026-06-30T23:59',
    },
  });
  expect(res.status()).toBe(201);
  return await res.json();
}

test.describe('League API', () => {
  test('create league returns league with _links.self and _links.game', async ({ request }) => {
    const eid = crypto.randomUUID();
    const res = await request.put(`${BASE}/api/league/${eid}?version=1`, {
      headers: headers('dev-admin'),
      data: {
        'game-eid': GAME_EID,
        name: 'Creation Test League',
        description: 'Testing league creation.',
      },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.type).toBe('league/league');
    expect(body.eid).toBe(eid);
    expect(body['created-by-sub']).toBe('dev-admin');
    expect(body._links.self).toContain(`/api/league/${eid}`);
    expect(body._links.game).toContain(`/api/game/${GAME_EID}`);
  });

  test('get league by eid returns the created league with HATEOAS links', async ({ request }) => {
    const leagueEid = await createLeague(request);
    const res = await request.get(`${BASE}/api/league/${leagueEid}`, {
      headers: headers('dev-admin'),
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.type).toBe('league/league');
    expect(body.eid).toBe(leagueEid);
    expect(body._links.self).toContain(`/api/league/${leagueEid}`);
  });
});

test.describe('Season API', () => {
  test('create season auto-assigns ordinal 1 for first season', async ({ request }) => {
    const leagueEid = await createLeague(request);
    const season = await createSeason(request, leagueEid);
    expect(season.type).toBe('season/season');
    expect(season.ordinal).toBe(1);
    expect(season['display-name']).toBe('Season 1');
    expect(season['league-eid']).toBe(leagueEid);
  });

  test('create second season auto-assigns ordinal 2', async ({ request }) => {
    const leagueEid = await createLeague(request);
    await createSeason(request, leagueEid);
    const second = await createSeason(request, leagueEid, {
      startAt: '2026-07-01T00:00',
      endAt: '2026-09-30T23:59',
    });
    expect(second.ordinal).toBe(2);
    expect(second['display-name']).toBe('Season 2');
  });

  test('create season rejects end before start', async ({ request }) => {
    const leagueEid = await createLeague(request);
    const eid = crypto.randomUUID();
    const res = await request.put(`${BASE}/api/season/${leagueEid}/${eid}`, {
      headers: headers('dev-admin'),
      data: {
        timezone: 'UTC',
        'start-at': '2026-12-31T23:59',
        'end-at': '2026-01-01T00:00',
      },
    });
    expect(res.status()).toBe(422);
    const body = await res.json();
    expect(body.type).toBe('season/error');
  });

  test('create season rejects non-owner', async ({ request }) => {
    const leagueEid = await createLeague(request);
    const eid = crypto.randomUUID();
    const res = await request.put(`${BASE}/api/season/${leagueEid}/${eid}`, {
      headers: headers('dev-player-one'),
      data: {
        timezone: 'UTC',
        'start-at': '2026-04-01T00:00',
        'end-at': '2026-06-30T23:59',
      },
    });
    expect(res.status()).toBe(422);
  });
});

test.describe('Tournament integration with league/season', () => {
  test('tournament with season-eid derives league-eid server-side', async ({ request }) => {
    const leagueEid = await createLeague(request);
    const season    = await createSeason(request, leagueEid);
    const tournEid  = crypto.randomUUID();
    const res = await request.put(`${BASE}/api/tournament/${tournEid}?version=1`, {
      headers: headers('dev-admin'),
      data: {
        'game-eid': GAME_EID,
        'season-eid': season.eid,
        name: 'Season Tournament',
        description: 'Attached to a season',
        timezone: 'UTC',
        'registration-opens-at': '2026-04-01T00:00',
        'registration-closes-at': '2030-04-30T23:59',
      },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body['league-eid']).toBe(leagueEid);
    expect(body['season-eid']).toBe(season.eid);
    expect(body._links.league).toContain(`/api/league/${leagueEid}`);
    expect(body._links.season).toContain(`/api/season/${season.eid}`);
  });

  test('standalone tournament has no league/season fields', async ({ request }) => {
    const tournEid = crypto.randomUUID();
    const res = await request.put(`${BASE}/api/tournament/${tournEid}?version=1`, {
      headers: headers('dev-admin'),
      data: {
        'game-eid': GAME_EID,
        name: 'Standalone Tournament',
        description: 'No league',
        timezone: 'UTC',
        'registration-opens-at': '2026-04-01T00:00',
        'registration-closes-at': '2030-04-30T23:59',
      },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body['league-eid']).toBeUndefined();
    expect(body['season-eid']).toBeUndefined();
    expect(body._links.league).toBeUndefined();
    expect(body._links.season).toBeUndefined();
  });
});

test.describe('Faction stats endpoints', () => {
  test('league + season scopes return well-formed empty rows when no matches recorded', async ({ request }) => {
    const leagueEid = await createLeague(request);
    const season    = await createSeason(request, leagueEid);
    for (const [url, scope] of [
      [`${BASE}/api/stats/season/${season.eid}/faction`, 'season'],
      [`${BASE}/api/stats/league/${leagueEid}/faction`,  'league'],
    ]) {
      const res = await request.get(url, { headers: headers('dev-admin') });
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.scope).toBe(scope);
      expect(Array.isArray(body.rows)).toBe(true);
    }
  });
});

test.describe('Competitive view + legacy URL', () => {
  test('legacy /tournament/index.html returns 404', async ({ request }) => {
    const res = await request.get(`${BASE}/view/game/${GAME_EID}/tournament/index.html`, {
      headers: { Accept: 'text/html', Cookie: 'dev_impersonation=dev-admin' },
    });
    expect(res.status()).toBe(404);
  });

  test('competitive landing renders both tabs', async ({ request }) => {
    const res = await request.get(`${BASE}/view/game/${GAME_EID}/competitive/index.html`, {
      headers: { Accept: 'text/html', Cookie: 'dev_impersonation=dev-admin' },
    });
    expect(res.status()).toBe(200);
    const body = await res.text();
    expect(body).toContain('panel-tournaments');
    expect(body).toContain('panel-leagues');
    expect(body).toContain('Competitive');
  });
});
