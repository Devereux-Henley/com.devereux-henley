// Series lobby panel (rts-xe7): the player check-in view reveals the single
// series lobby code once both sides have checked in. The player console is a
// demo surface until rts-pwd wires real view-models, so this exercises the
// template + component rendering end to end (code, copy, host/format/patch/
// reinforcements, caster, launcher). Touching components/e2e also keeps the
// brick in the poly test set for this PR.

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

async function createTournament(request) {
  const eid = crypto.randomUUID();
  const res = await request.put(`${BASE}/actions/tournament/${eid}?version=1`, {
    headers: actionHeaders('dev-admin'),
    data: {
      'game-eid': GAME_EID,
      name: 'E2E Lobby Tournament',
      description: 'Created by the series lobby e2e test.',
      timezone: 'UTC',
      'registration-opens-at': '2020-01-01T00:00',
      'registration-closes-at': '2030-01-01T00:00',
    },
  });
  expect(res.status()).toBe(200);
  return eid;
}

test.describe('Series lobby panel', () => {
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

  test('check-in view reveals the series lobby code and setup', async ({ page, request }) => {
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/player/check-in.html`);

    const lobby = page.locator('.lobby-panel');
    await expect(lobby).toBeVisible();
    await expect(lobby.locator('#lobby-heading')).toContainText('Series Lobby');

    // One thematic code (WORD-XXXX) issued for the whole series.
    await expect(lobby.locator('.lobby-code')).toHaveText(/^[A-Z]+-[0-9A-F]{4}$/);

    // Host / Format / Patch / Reinforcements setup the host applies.
    await expect(lobby.locator('.lobby-fields dt')).toHaveText(
      ['Host', 'Format', 'Patch', 'Reinforcements'],
    );

    // Caster spectator slot and the launcher shortcut.
    await expect(lobby.locator('.lobby-caster')).toContainText('Caster spectator');
    await expect(lobby.locator('a.btn-primary-lg', { hasText: 'Open in launcher' })).toBeVisible();
  });

  test('copy button copies the lobby code and confirms', async ({ page, request, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    const eid = await createTournament(request);
    await page.goto(`/view/game/${GAME_EID}/tournament/${eid}/player/check-in.html`);

    const code = await page.locator('.lobby-code').textContent();
    const copy = page.locator('.lobby-copy');
    await copy.click();
    await expect(copy).toContainText('Copied');

    const clipboard = await page.evaluate(() => navigator.clipboard.readText());
    expect(clipboard).toBe(code.trim());
  });
});
