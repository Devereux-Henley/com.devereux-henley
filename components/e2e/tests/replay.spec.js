const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const BASE = process.env.RTS_API_BASE_URL || 'http://127.0.0.1:3001';
const SAMPLE_REPLAY = path.resolve(__dirname, '../fixtures/sample-battle.replay');

const AUTH = { Cookie: 'dev_impersonation=dev-admin' };

async function uploadReplay(request, replayEid = crypto.randomUUID()) {
  const buffer = fs.readFileSync(SAMPLE_REPLAY);
  const res = await request.put(`${BASE}/api/replay/${replayEid}`, {
    headers: { Accept: 'application/hal+json', ...AUTH },
    multipart: {
      file: {
        name: 'sample-battle.replay',
        mimeType: 'application/octet-stream',
        buffer,
      },
    },
  });
  return { status: res.status(), body: await res.json(), eid: replayEid };
}

test.describe('Replay API (application/hal+json)', () => {
  test('upload parses the file and returns a replay resource', async ({ request }) => {
    const { status, body } = await uploadReplay(request);

    expect(status).toBe(201);
    expect(body.type).toBe('replay/replay');
    expect(body['match-id']).toBe('7801776992105');
    expect(body['parser-format']).toBe('CBAB');
    expect(body['victory-condition']).toBe('BATTLE_SETUP_VICTORY_CONDITION_CAPTURE_LOCATION_SCORE');
    expect(body['winning-alliance-idx']).toBeNull();
    expect(body['uploaded-by-sub']).toBe('dev-admin');
    expect(body._links).toBeDefined();
    expect(body._links.self).toContain(`/api/replay/${body.eid}`);
  });

  test('upload exposes drafted unit lists per alliance', async ({ request }) => {
    const { body } = await uploadReplay(request);

    expect(body.alliances).toHaveLength(2);

    const [empire, chaosDwarfs] = body.alliances;
    expect(empire['faction-key']).toBe('wh_main_emp_empire');
    expect(chaosDwarfs['faction-key']).toBe('wh3_dlc23_chd_legion_of_azgorh');

    const mainEmpireArmy = empire.armies.find((a) => !a['is-reinforcement']);
    expect(mainEmpireArmy['commander-display']).toBe('Master Engineer');
    expect(mainEmpireArmy.units[0].key).toBe('wh3_dlc25_emp_cha_master_engineer_steam_tank');
    expect(mainEmpireArmy.units.length).toBeGreaterThan(10);

    const unitKeys = mainEmpireArmy.units.map((u) => u.key);
    expect(unitKeys).toContain('wh_main_emp_inf_halberdiers');
    expect(unitKeys).toContain('wh_main_emp_inf_greatswords');
  });

  test('GET /api/replay/:eid returns the persisted resource', async ({ request }) => {
    const { eid } = await uploadReplay(request);

    const res = await request.get(`${BASE}/api/replay/${eid}`, {
      headers: { Accept: 'application/hal+json', ...AUTH },
    });
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.eid).toBe(eid);
    expect(body['match-id']).toBe('7801776992105');
    expect(body.alliances).toHaveLength(2);
  });

  test('GET /api/replay/:eid returns 404 for unknown eid', async ({ request }) => {
    const unknown = '00000000-0000-4000-8000-000000000000';
    const res = await request.get(`${BASE}/api/replay/${unknown}`, {
      headers: { Accept: 'application/hal+json', ...AUTH },
    });
    expect(res.status()).toBe(404);
    const body = await res.json();
    expect(body.type).toBe('missing/resource');
  });

  test('PUT /api/replay/:eid/winner declares the winning alliance', async ({ request }) => {
    const { eid } = await uploadReplay(request);

    const res = await request.put(`${BASE}/api/replay/${eid}/winner`, {
      headers: {
        Accept: 'application/hal+json',
        'Content-Type': 'application/json',
        ...AUTH,
      },
      data: { 'winning-alliance-idx': 0 },
    });
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body['winning-alliance-idx']).toBe(0);
    expect(body.type).toBe('replay/replay');
  });

  test('winner declaration accepts -1 for a draw', async ({ request }) => {
    const { eid } = await uploadReplay(request);

    const res = await request.put(`${BASE}/api/replay/${eid}/winner`, {
      headers: {
        Accept: 'application/hal+json',
        'Content-Type': 'application/json',
        ...AUTH,
      },
      data: { 'winning-alliance-idx': -1 },
    });
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body['winning-alliance-idx']).toBe(-1);
  });

});

test.describe('Replay views', () => {
  test.beforeEach(async ({ context }) => {
    await context.addCookies([
      { name: 'dev_impersonation', value: 'dev-admin', url: BASE },
    ]);
  });

  test('upload form renders', async ({ page }) => {
    await page.goto(`${BASE}/view/replay/upload.html`);
    await expect(page).toHaveTitle(/Upload Replay/);
    await expect(page.locator('input#replay-file')).toBeVisible();
    await expect(page.locator('button[type="submit"]')).toContainText('Upload');
  });

  test('my-replays page shows empty state for a fresh user', async ({ page, context }) => {
    // Use dev-player-two to avoid leakage from other tests that uploaded as dev-admin.
    await context.clearCookies();
    await context.addCookies([
      { name: 'dev_impersonation', value: 'dev-player-two', url: BASE },
    ]);
    await page.goto(`${BASE}/view/replay/me.html`);
    await expect(page).toHaveTitle(/My Replays/);
    await expect(page.locator('section')).toContainText('No replays uploaded yet.');
  });

  test('replay detail page renders drafted units and winner form', async ({ page, request }) => {
    const { eid } = await uploadReplay(request);

    await page.goto(`${BASE}/view/replay/${eid}/index.html`);
    await expect(page).toHaveTitle(/Replay 7801776992105/);
    await expect(page.locator('h2#replay-heading')).toContainText('7801776992105');

    // Faction heading + at least one known unit key renders as <code>.
    await expect(page.locator('section.replay-alliance').first()).toContainText('wh_main_emp_empire');
    await expect(page.locator('ul.replay-unit-list code').first()).toContainText('wh3_dlc25_emp_cha_master_engineer_steam_tank');

    // Winner select present and initially unset.
    await expect(page.locator('select#winning-alliance-idx')).toBeVisible();
  });

  test('missing replay renders 404 page', async ({ page }) => {
    const unknown = '00000000-0000-4000-8000-000000000001';
    const response = await page.goto(`${BASE}/view/replay/${unknown}/index.html`);
    expect(response.status()).toBe(404);
    await expect(page.locator('h2#missing-replay-heading')).toContainText('Replay not found');
  });
});
