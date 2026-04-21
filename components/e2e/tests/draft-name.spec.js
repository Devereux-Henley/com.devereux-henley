const { test, expect } = require('@playwright/test');

const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const HIGH_ELVES_FACTION_EID = 'f000000a-0000-4000-8000-000000000000';
const LAND_BATTLE_MODE_EID = 'a1b2c3d4-0001-4000-8000-000000000001';

function addDevCookie(context) {
  return context.addCookies([
    {
      name: 'dev_impersonation',
      value: 'dev-admin',
      domain: 'localhost',
      path: '/',
      httpOnly: true,
      sameSite: 'Lax',
    },
  ]);
}

async function createHighElvesDraft(page) {
  await page.goto(`/view/game/${GAME_EID}/draft/create.html`);
  await page.locator('#faction-eid').selectOption(HIGH_ELVES_FACTION_EID);
  await page.locator('#game-mode-eid').selectOption(LAND_BATTLE_MODE_EID);
  await page.locator('#create-draft-form button[type="submit"]').click();
  await expect(page.locator('.draft-page')).toBeVisible({ timeout: 10000 });
}

test.describe.serial('Draft rename', () => {
  test.beforeEach(async ({ context }) => {
    await addDevCookie(context);
  });

  test('new draft shows faction-and-date placeholder, not a pre-filled name', async ({ page }) => {
    await createHighElvesDraft(page);
    const input = page.locator('#draft-name-input');
    await expect(input).toBeVisible();
    await expect(input).toHaveValue('');
    await expect(input).toHaveAttribute('placeholder', /High Elves draft \d{2}\/\d{2}\/\d{4}/);
    await expect(page).toHaveTitle(/High Elves draft \d{2}\/\d{2}\/\d{4}/);
  });

  test('typing a custom name persists, re-titles the page, and appears in the My Drafts list', async ({ page }) => {
    await createHighElvesDraft(page);
    const input = page.locator('#draft-name-input');
    await input.fill('Teclis Expeditionary Force');
    await input.dispatchEvent('input');

    // HTMX PATCH is debounced 500ms. Wait for the "saved" affordance to fire.
    await expect(page.locator('.draft-nameplate')).toHaveClass(/draft-nameplate--saved/, { timeout: 3000 });

    await page.reload();
    await expect(page.locator('#draft-name-input')).toHaveValue('Teclis Expeditionary Force');
    await expect(page).toHaveTitle(/Teclis Expeditionary Force/);

    // My Drafts list shows the custom name, not the default. The dev-admin
    // DB may accumulate prior renamed drafts across runs — we just need
    // at least one match.
    await page.goto(`/view/game/${GAME_EID}/draft/me.html`);
    await expect(page.locator('a', { hasText: 'Teclis Expeditionary Force' }).first()).toBeVisible();
  });

  test('clearing the field restores the faction-and-date default', async ({ page }) => {
    await createHighElvesDraft(page);
    const input = page.locator('#draft-name-input');
    await input.fill('Throwaway');
    await input.dispatchEvent('input');
    await expect(page.locator('.draft-nameplate')).toHaveClass(/draft-nameplate--saved/, { timeout: 3000 });

    // Clear — empty string should revert to the default on reload.
    await page.locator('.draft-nameplate').evaluate((el) => el.classList.remove('draft-nameplate--saved'));
    await input.fill('');
    await input.dispatchEvent('input');
    await expect(page.locator('.draft-nameplate')).toHaveClass(/draft-nameplate--saved/, { timeout: 3000 });

    await page.reload();
    await expect(page.locator('#draft-name-input')).toHaveValue('');
    await expect(page).toHaveTitle(/High Elves draft \d{2}\/\d{2}\/\d{4}/);
  });
});
