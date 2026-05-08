const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const BASE = process.env.RTS_API_BASE_URL || 'http://127.0.0.1:3001';
const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const SAMPLE_REPLAY = path.resolve(__dirname, '../fixtures/sample-battle.replay');
// Replay carrying veteranned units: a level-9 Aspiring Champions and a level-1
// + level-3 pair of Chosen of Khorne (DW). Used to exercise the chevron-badge
// + adjusted-cost rendering path in the post-match modal.
const LEVELED_REPLAY = path.resolve(__dirname, '../fixtures/leveled-battle.replay');
// Replay carrying mount-encoded engine keys whose un-mounted base row would
// price too low if cost were reconstructed from the seed: a Master Engineer
// on Steam Tank (engine cost 3100 vs un-mounted ~900). Used to exercise the
// parser-emitted-cost branch in enrich-unit.
const MOUNT_VARIANT_REPLAY = path.resolve(__dirname, '../fixtures/mount-variant-battle.replay');

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

async function postParseFragment(request, matchEid, count, replayPath = SAMPLE_REPLAY) {
  const buffer = fs.readFileSync(replayPath);
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
    expect(html).toMatch(/<dialog[^>]*\bopen\b/);
    expect(html).toContain('pm-panel--upload');
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
    expect(html).toContain('pm-panel--review');
    // Each draft unit tile renders a portrait <img> sourced from /card/unit/<eid>.png
    // (or the placeholder fallback when the parser key didn't enrich against the DB).
    expect(html).toMatch(/<img class="pm-draft-unit-portrait"[^>]*src="\/card\/unit\/[^"]+\.png"/);
    expect(html).toMatch(/onerror="this\.src='\/card\/unit\/placeholder\.png'/);
    // Each side splits its army into a Main Army and a Reinforcements section
    // — both label headings must appear for both players (p1 and p2), with
    // matching aria-labelledby ids so the section headings are screen-reader
    // accessible.
    for (const side of ['p1', 'p2']) {
      expect(html).toMatch(new RegExp(`id="pm-section-0-${side}-main"[^>]*>Main Army</h4>`));
      expect(html).toMatch(new RegExp(`id="pm-section-0-${side}-reinforcements"[^>]*>Reinforcements</h4>`));
      expect(html).toContain(`aria-labelledby="pm-section-0-${side}-main"`);
      expect(html).toContain(`aria-labelledby="pm-section-0-${side}-reinforcements"`);
    }
    // Aggregate "X units across N armies" footer is gone now that sections are
    // explicit — only the commander remains in the per-side foot.
    expect(html).not.toMatch(/units across \d+ arm/);
    // Faction-key resolution: the parser emits engine ids like
    // `wh_main_emp_empire` / `wh3_dlc23_chd_legion_of_azgorh` for the
    // sample replay.  After resolution the visible labels render the
    // parent race name only (the lord's specific subfaction is dropped —
    // it isn't relevant for comp play); the raw engine ids only survive
    // in the hidden parsed-N JSON blob.
    expect(html).toMatch(/pm-draft-handle-faction[^>]*>\s*The Empire\s*</);
    expect(html).toMatch(/pm-draft-handle-faction[^>]*>\s*Chaos Dwarfs\s*</);
    const visibleHtml = html.replace(/value="[^"]*"/g, '');
    expect(visibleHtml).not.toContain('wh_main_emp_empire');
    expect(visibleHtml).not.toContain('wh3_dlc23_chd_legion_of_azgorh');
    expect(visibleHtml).not.toContain('Reikland');
    expect(visibleHtml).not.toContain('Legion of Azgorh');
  });

  test('renders chevron badges and adjusted costs for veteranned units', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 1);

    const res = await postParseFragment(request, matchEid, 1, LEVELED_REPLAY);
    expect(res.status()).toBe(200);
    const html = await res.text();

    // The leveled fixture has three veteranned units: L9 Aspiring Champions
    // (base 1100 → adjusted 1496) and L1 + L3 Chosen of Khorne (base 1450 →
    // adjusted 1505 / 1614). Each one renders its rank badge in the corner of
    // the portrait + the adjusted cost in the existing pip.
    expect(html).toMatch(/<span class="pm-draft-unit-level" aria-label="Veteran rank 9">9<\/span>/);
    expect(html).toMatch(/<span class="pm-draft-unit-level" aria-label="Veteran rank 1">1<\/span>/);
    expect(html).toMatch(/<span class="pm-draft-unit-level" aria-label="Veteran rank 3">3<\/span>/);
    expect(html).toMatch(/<span class="pm-draft-unit-cost">1496<\/span>/);
    expect(html).toMatch(/<span class="pm-draft-unit-cost">1505<\/span>/);
    expect(html).toMatch(/<span class="pm-draft-unit-cost">1614<\/span>/);

    // Unleveled units in the same draft (Skavenslaves, Daemon Prince, base
    // Aspiring Champions) must not carry a level badge — the conditional in
    // the template is gated on `unit.leveled?` (level > 0).
    const badgeCount = (html.match(/class="pm-draft-unit-level"/g) || []).length;
    expect(badgeCount).toBe(3);

    // The Chaos side total reflects the adjusted costs end to end. With the
    // replay's roster — Daemon Prince 1800, Asp Champions 1100 + 1496,
    // Chosen 1250, Chosen DW 1505 + 1614 — the side header reads 8765 pts.
    expect(html).toMatch(/pm-draft-cost-num">\s*8765\s*</);
  });

  test('renders engine-resolved cost for mount-variant unit keys', async ({ request }) => {
    const tournamentEid = await createActiveTournament(request);
    const matchEid = await createMatch(request, tournamentEid, 1);

    const res = await postParseFragment(request, matchEid, 1, MOUNT_VARIANT_REPLAY);
    expect(res.status()).toBe(200);
    const html = await res.text();

    // Master Engineer on Steam Tank — the engine key
    // wh3_dlc25_emp_cha_master_engineer_steam_tank resolves via prefix-fallback
    // to the un-mounted Master Engineer row whose seed cost is ~900. The
    // parser emits child[48] (engine-resolved final cost = 3100) and
    // enrich-unit prefers it over the seed reconstruction, so the tile shows
    // the full mount-included cost.
    expect(html).toMatch(/<span class="pm-draft-unit-cost">3100<\/span>/);

    // Sanity: the un-mounted Skaven Warlord on the other side renders the
    // engine-resolved baseline cost (1075), not the seed display base (525).
    expect(html).toMatch(/<span class="pm-draft-unit-cost">1075<\/span>/);
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
    expect(html).toContain('pm-panel--submitted');
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
