const { test, expect } = require('@playwright/test');

const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';

test.describe('Authenticated draft flows', () => {
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

  test('navbar shows user name when authenticated', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/index.html`);
    await expect(page.locator('button[aria-label*="Account menu"]')).toBeVisible();
  });

  test('my drafts link appears in game context', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/index.html`);
    const myDraftsLink = page.locator('a[href*="/draft/me.html"]');
    await expect(myDraftsLink).toBeVisible();
  });

  test('my drafts page loads', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/draft/me.html`);
    await expect(page).toHaveTitle(/Drafts/);
    await expect(page.locator('main#content')).toBeVisible();
  });

  test('create draft page loads with form', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/draft/create.html`);
    await expect(page).toHaveTitle(/Create.*Draft/i);
    await expect(page.locator('main#content')).toBeVisible();
  });

  test('account dropdown shows logout option', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/index.html`);

    const accountButton = page.locator('button[aria-label*="Account menu"]');
    await accountButton.click();

    const profileMenu = page.locator('#profile-menu');
    await expect(profileMenu).toBeVisible();
    await expect(profileMenu.locator('a[href="/view/logout.html"]')).toBeVisible();
  });
});
