// Touches components/e2e so poly test re-runs the brick on PRs that only
// modify upstream handlers/queries. The long-term fix is rts-9w4 (explicit
// brick deps); until then, every domain-migration PR carries a small
// e2e touch.
//
// Covers the replay submission flow after its migration to Datalevin
// (rts-23h / rts-7jf): upload + parse a real replay fixture, confirm the
// review fragment renders the parsed armies, submit the parsed payload, and
// confirm the resulting match_game + replay persist and render on the match
// detail page.

const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const BASE = process.env.RTS_API_BASE_URL || 'http://127.0.0.1:3001';
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const FIXTURE = path.join(__dirname, '..', 'fixtures', 'sample-battle.replay');

function actionHeaders(user) {
  return {
    Accept: 'application/htmx+html',
    'Content-Type': 'application/json',
    Cookie: `dev_impersonation=${user}`,
  };
}

function htmlHeaders(user) {
  return { Accept: 'text/html', Cookie: `dev_impersonation=${user}` };
}

async function createTournament(request) {
  const eid = crypto.randomUUID();
  const res = await request.put(`${BASE}/actions/tournament/${eid}?version=1`, {
    headers: actionHeaders('dev-admin'),
    data: {
      'game-eid': GAME_EID,
      name: 'Replay E2E Tournament',
      description: 'Created by replay e2e test.',
      timezone: 'UTC',
      'registration-opens-at': '2020-01-01T00:00',
      'registration-closes-at': '2030-01-01T00:00',
    },
  });
  expect(res.status()).toBe(200);
  return eid;
}

async function configureSinglePhase(request, eid) {
  const res = await request.put(`${BASE}/api/tournament-phase-configuration?tournament-eid=${eid}`, {
    headers: { ...htmlHeaders('dev-admin'), 'Content-Type': 'application/json' },
    data: {
      phases: [{ 'phase-type': 'single-elimination', rounds: [{ 'round-index': 0, format: 1 }] }],
      'qualifier-count': 2,
    },
  });
  expect(res.status()).toBe(200);
}

async function enter(request, eid, user) {
  const res = await request.post(`${BASE}/actions/tournament/${eid}/entry/me`, {
    headers: actionHeaders(user),
  });
  expect(res.status()).toBe(201);
}

async function startTournament(request, eid) {
  const res = await request.post(`${BASE}/actions/tournament/${eid}/start`, {
    headers: actionHeaders('dev-admin'),
  });
  expect(res.status()).toBe(200);
}

async function generateRound(request, eid) {
  const res = await request.post(`${BASE}/actions/tournament/${eid}/round`, {
    headers: actionHeaders('dev-admin'),
  });
  expect(res.status()).toBe(200);
}

async function firstMatchEid(request, eid) {
  const res = await request.get(`${BASE}/api/match?tournament-eid=${eid}`, {
    headers: htmlHeaders('dev-admin'),
  });
  expect(res.status()).toBe(200);
  const html = await res.text();
  const m = html.match(/\/match\/([0-9a-f-]{36})/);
  expect(m, 'a match link should appear in the match collection').not.toBeNull();
  return m[1];
}

// Reverse of the HTML attribute escaping applied to the hidden parsed-json
// input so the JSON round-trips back through the submit endpoint intact.
function unescapeAttr(s) {
  return s
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&');
}

function hiddenValue(html, name) {
  const re = new RegExp(`name="${name}"[^>]*value="([^"]*)"`);
  const m = html.match(re);
  return m ? unescapeAttr(m[1]) : null;
}

async function setupMatch(request) {
  const eid = await createTournament(request);
  await configureSinglePhase(request, eid);
  await enter(request, eid, 'dev-player-one');
  await enter(request, eid, 'dev-player-two');
  await startTournament(request, eid);
  await generateRound(request, eid);
  const matchEid = await firstMatchEid(request, eid);
  return { eid, matchEid };
}

test.describe('Replay submission (Datalevin)', () => {
  test('parse renders the review fragment with armies', async ({ request }) => {
    const { eid, matchEid } = await setupMatch(request);
    const res = await request.post(
      `${BASE}/actions/tournament/${eid}/match/${matchEid}/replay/parse`,
      {
        headers: { Accept: 'application/htmx+html', Cookie: 'dev_impersonation=dev-player-one' },
        multipart: {
          'game-0': {
            name: 'sample-battle.replay',
            mimeType: 'application/octet-stream',
            buffer: fs.readFileSync(FIXTURE),
          },
        },
      },
    );
    expect(res.status()).toBe(200);
    const html = await res.text();
    expect(html).not.toContain('pm-error');
    // Review fragment echoes the parsed payload + auto-detected winner.
    expect(hiddenValue(html, 'parsed-json')).not.toBeNull();
    expect(hiddenValue(html, 'winner-sub')).toBeTruthy();
    expect(html.toLowerCase()).toContain('army');
  });

  test('submit persists the game and it renders on the match detail page', async ({ request }) => {
    const { eid, matchEid } = await setupMatch(request);

    const parseRes = await request.post(
      `${BASE}/actions/tournament/${eid}/match/${matchEid}/replay/parse`,
      {
        headers: { Accept: 'application/htmx+html', Cookie: 'dev_impersonation=dev-player-one' },
        multipart: {
          'game-0': {
            name: 'sample-battle.replay',
            mimeType: 'application/octet-stream',
            buffer: fs.readFileSync(FIXTURE),
          },
        },
      },
    );
    expect(parseRes.status()).toBe(200);
    const parseHtml = await parseRes.text();
    const parsedJson = hiddenValue(parseHtml, 'parsed-json');
    const winnerSub = hiddenValue(parseHtml, 'winner-sub');
    expect(parsedJson).not.toBeNull();

    const submitRes = await request.post(
      `${BASE}/actions/tournament/${eid}/match/${matchEid}/replay/submit`,
      {
        headers: { Accept: 'application/htmx+html', Cookie: 'dev_impersonation=dev-player-one' },
        form: {
          'parsed-json': parsedJson,
          'winner-sub': winnerSub,
          'source-name': 'sample-battle.replay',
        },
      },
    );
    expect(submitRes.status()).toBe(200);
    // Mutations signal success via HX-Trigger, never a structured body.
    expect(submitRes.headers()['hx-trigger-after-settle']).toContain('match-game-recorded');
    const submitHtml = await submitRes.text();
    expect(submitHtml.toLowerCase()).toContain('game 1 submitted');

    // The recorded game + its replay persist and render on the match detail page.
    const matchRes = await request.get(`${BASE}/api/match/${matchEid}`, {
      headers: htmlHeaders('dev-admin'),
    });
    expect(matchRes.status()).toBe(200);
    const matchHtml = await matchRes.text();
    expect(matchHtml).toContain(winnerSub);
    expect(matchHtml.toLowerCase()).toContain('complete');

    const gamesRes = await request.get(`${BASE}/api/match-game?match-eid=${matchEid}`, {
      headers: htmlHeaders('dev-admin'),
    });
    expect(gamesRes.status()).toBe(200);
    expect((await gamesRes.text()).toLowerCase()).toContain('game');
  });
});
