const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const BASE = process.env.RTS_API_BASE_URL || 'http://127.0.0.1:3001';
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const SAMPLE_REPLAY = path.resolve(__dirname, '../fixtures/sample-battle.replay');

function jsonHeaders(user) {
  return {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Cookie: `dev_impersonation=${user}`,
  };
}

async function createTournament(request) {
  const eid = crypto.randomUUID();
  const res = await request.put(`${BASE}/api/tournament/${eid}?version=1`, {
    headers: jsonHeaders('dev-admin'),
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
    headers: jsonHeaders('dev-admin'),
    data: { status: 'active' },
  });
  return eid;
}

async function createMatch(request, tournamentEid, format = 3) {
  const res = await request.post(`${BASE}/api/tournament/${tournamentEid}/match`, {
    headers: jsonHeaders('dev-admin'),
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

async function postParseFragment(request, matchEid, count) {
  const buffer = fs.readFileSync(SAMPLE_REPLAY);
  const multipart = {};
  for (let i = 0; i < count; i++) {
    multipart[`game-${i}`] = {
      name: `g${i}.replay`,
      mimeType: 'application/octet-stream',
      buffer,
    };
  }
  return request.post(`${BASE}/view/match-record/${matchEid}/parse`, {
    headers: { Cookie: 'dev_impersonation=dev-admin' },
    multipart,
  });
}

async function postSubmitFragment(request, matchEid, fields) {
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(fields)) params.append(k, v);
  return request.post(`${BASE}/view/match-record/${matchEid}/submit`, {
    headers: {
      Cookie: 'dev_impersonation=dev-admin',
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    data: params.toString(),
  });
}

test.describe('Match-record modal view', () => {
  test('returns the modal HTML for a real match', async ({ request }) => {
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
    expect(html).toContain(`hx-post="/view/match-record/${matchEid}/parse"`);
  });

  test('returns 404 for unknown match-eid', async ({ request }) => {
    const fakeEid = '00000000-0000-4000-8000-000000000000';
    const res = await request.get(`${BASE}/view/match-record/${fakeEid}/index.html`, {
      headers: { Accept: 'text/html', Cookie: 'dev_impersonation=dev-admin' },
    });
    expect(res.status()).toBe(404);
  });
});

test.describe('Parse fragment endpoint', () => {
  test('returns the Step-3 review fragment HTML with hidden parsed JSON', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 3);

    const res = await postParseFragment(request, matchEid, 3);
    expect(res.status()).toBe(200);
    const html = await res.text();
    expect(html).toContain('id="pm-form-submit"');
    expect(html).toContain(`hx-post="/view/match-record/${matchEid}/submit"`);
    expect(html).toContain('name="parsed-0"');
    expect(html).toContain('name="parsed-1"');
    expect(html).toContain('name="parsed-2"');
    expect(html).toContain('name="winner-sub-0"');
    // Inline script flips data-pm-step to review.
    expect(html).toContain("dataset.pmStep = 'review'");
  });

  test('rejects empty submission with an inline error fragment', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 1);

    const res = await request.post(`${BASE}/view/match-record/${matchEid}/parse`, {
      headers: { Cookie: 'dev_impersonation=dev-admin' },
      multipart: {},
    });
    expect(res.status()).toBe(422);
    const html = await res.text();
    expect(html).toContain('class="pm-error"');
    expect(html).toContain('No replay files');
  });
});

test.describe('Submit fragment endpoint', () => {
  test('records a Bo1 match and returns the Step-4 submitted fragment', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 1);

    // Run the parse step to obtain a valid hidden parsed JSON.
    const parseRes = await postParseFragment(request, matchEid, 1);
    const parseHtml = await parseRes.text();
    // Extract the hidden parsed-0 value (the template HTML-escapes it, so we
    // unescape the &quot; sequences before sending it back).
    const match = parseHtml.match(/name="parsed-0"\s+value="([^"]*)"/);
    expect(match).not.toBeNull();
    const parsedJson = match[1].replace(/&quot;/g, '"').replace(/&amp;/g, '&');

    const submitRes = await postSubmitFragment(request, matchEid, {
      'parsed-0': parsedJson,
      'source-name-0': 'g0.replay',
      'winner-sub-0': 'dev-admin',
    });
    expect(submitRes.status()).toBe(201);
    const html = await submitRes.text();
    expect(html).toContain('Match recorded');
    expect(html).toContain('dev-admin takes the series 1–0');
    expect(html).toContain("dataset.pmStep = 'submitted'");
  });

  test('rejects an undecided Bo3 submission with the inline error fragment', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 3);

    const parseRes = await postParseFragment(request, matchEid, 3);
    const parseHtml = await parseRes.text();
    const matches = [...parseHtml.matchAll(/name="parsed-(\d+)"\s+value="([^"]*)"/g)];
    expect(matches).toHaveLength(3);
    const parsed = (i) => matches[i][2].replace(/&quot;/g, '"').replace(/&amp;/g, '&');

    // 1-1 split — series undecided.
    const submitRes = await postSubmitFragment(request, matchEid, {
      'parsed-0': parsed(0),
      'source-name-0': 'g0.replay',
      'winner-sub-0': 'dev-admin',
      'parsed-1': parsed(1),
      'source-name-1': 'g1.replay',
      'winner-sub-1': 'dev-player-one',
      'parsed-2': parsed(2),
      'source-name-2': 'g2.replay',
      // no winner declared for game 2
    });
    expect(submitRes.status()).toBe(422);
    const html = await submitRes.text();
    expect(html).toContain('class="pm-error"');
    expect(html).toMatch(/do not decide the series/);
  });
});
