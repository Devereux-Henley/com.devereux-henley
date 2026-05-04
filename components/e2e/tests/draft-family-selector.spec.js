// Regression: the Mark and Lore selectors must each submit through
// their own form so a change on one doesn't carry the other's value
// as a duplicate `unit-eid` query param (which used to fail the
// single-uuid coercion with HTTP 400).
const { test, expect } = require('@playwright/test');

const GAME            = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const WOC_FACTION_EID = 'f0000017-0000-4000-8000-000000000000';
const LAND_BATTLE     = 'a1b2c3d4-0001-4000-8000-000000000001';

test('mark + lore selectors swap unit-eid without 400s', async ({ page, context }) => {
  await context.addCookies([{
    name:     'dev_impersonation',
    value:    'dev-admin',
    domain:   'localhost',
    path:     '/',
    httpOnly: true,
    sameSite: 'Lax',
  }]);

  const failures = [];
  page.on('response', resp => {
    if (resp.status() === 400) {
      failures.push(`${resp.request().method()} ${resp.url()}`);
    }
  });

  await page.goto(`/view/game/${GAME}/draft/create.html`);
  await page.locator('#faction-eid').selectOption(WOC_FACTION_EID);
  await page.locator('#game-mode-eid').selectOption(LAND_BATTLE);
  await page.locator('#create-draft-form button[type="submit"]').click();
  await expect(page.locator('.draft-page')).toBeVisible({ timeout: 10000 });

  await page.locator('.draft-unit-card[aria-label^="Chaos Sorcerer Lord"]').first().click();
  await expect(page.locator('#draft-mark-select')).toBeVisible({ timeout: 5000 });

  await page.locator('#draft-mark-select').selectOption({ index: 1 });
  await expect(page.locator('#draft-mark-select')).toBeVisible({ timeout: 5000 });

  await page.locator('#draft-lore-select').selectOption({ index: 1 });
  await expect(page.locator('#draft-lore-select')).toBeVisible({ timeout: 5000 });

  expect(failures, `Got 400s: ${failures.join(', ')}`).toHaveLength(0);
});
