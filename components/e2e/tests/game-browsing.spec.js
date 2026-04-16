const { test, expect } = require('@playwright/test');

const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const EMPIRE_FACTION_EID = '35dd38fa-2bcc-4492-8f58-a106d0d02cbb';

test.describe('Game browsing', () => {
  test('game index page loads with factions', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/index.html`);
    await expect(page).toHaveTitle(/Warhammer III/);
    await expect(page.locator('main#content')).toBeVisible();
    await expect(page.locator('#factions-dropdown-btn')).toBeVisible();
  });

  test('faction page loads with units', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/faction/${EMPIRE_FACTION_EID}/index.html`);
    await expect(page).toHaveTitle(/The Empire/);
    await expect(page.locator('main#content')).toBeVisible();
  });

  test('factions dropdown populates after selecting a game', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/index.html`);

    const factionsButton = page.locator('#factions-dropdown-btn');
    await expect(factionsButton).toBeVisible();
    await factionsButton.click();

    const factionsMenu = page.locator('#factions-menu');
    await expect(factionsMenu).toBeVisible();

    const factionLinks = factionsMenu.locator('a.dropdown-item');
    const count = await factionLinks.count();
    expect(count).toBeGreaterThan(0);

    await expect(factionLinks.first()).toBeVisible();
  });

  test('clicking a faction link navigates to faction page', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/index.html`);

    const factionsButton = page.locator('#factions-dropdown-btn');
    await factionsButton.click();

    const firstFaction = page.locator('#factions-menu a.dropdown-item').first();
    const factionName = await firstFaction.textContent();
    await firstFaction.click();

    await expect(page.locator('main#content')).toBeVisible();
    await expect(page).toHaveTitle(new RegExp(factionName.trim()));
  });
});
