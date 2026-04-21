const { test, expect } = require('@playwright/test');

const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const EMPIRE_FACTION_EID = '35dd38fa-2bcc-4492-8f58-a106d0d02cbb';

test.describe('Game browsing', () => {
  test('game index page loads with atlas dropdown', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/index.html`);
    await expect(page).toHaveTitle(/Warhammer III/);
    await expect(page.locator('main#content')).toBeVisible();
    await expect(page.locator('#atlas-dropdown-btn')).toBeVisible();
  });

  test('faction page loads with units', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/faction/${EMPIRE_FACTION_EID}/index.html`);
    await expect(page).toHaveTitle(/The Empire/);
    await expect(page.locator('main#content')).toBeVisible();
  });

  test('atlas dropdown links to the faction list', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/index.html`);

    const atlasButton = page.locator('#atlas-dropdown-btn');
    await expect(atlasButton).toBeVisible();
    await atlasButton.click();

    const atlasMenu = page.locator('#atlas-menu');
    await expect(atlasMenu).toBeVisible();

    const factionsLink = atlasMenu.locator('a.dropdown-item', { hasText: 'Factions' });
    await expect(factionsLink).toBeVisible();
  });

  test('clicking a faction card navigates to faction page', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/faction/index.html`);

    const factionCards = page.locator('a.faction-card');
    await expect(factionCards.first()).toBeVisible();

    const firstFaction = factionCards.first();
    const factionName = (await firstFaction.locator('.faction-card-name').textContent()).trim();
    await firstFaction.click();

    await expect(page.locator('main#content')).toBeVisible();
    await expect(page).toHaveTitle(new RegExp(factionName));
  });
});
