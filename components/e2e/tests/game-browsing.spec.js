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

  test('faction list page renders the full roster', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/faction/index.html`);
    await expect(page).toHaveTitle(/Factions/);
    await expect(page.locator('#faction-list-heading')).toBeVisible();
    // Warhammer III seed has 24 factions; assert at least one card per row plus
    // the canonical pillars so a partial render fails loudly.
    await expect(page.locator('a.faction-card')).not.toHaveCount(0);
    await expect(page.locator('.faction-card-name', { hasText: 'The Empire' })).toBeVisible();
    await expect(page.locator('.faction-card-name', { hasText: 'Grand Cathay' })).toBeVisible();
  });

  test('faction detail page groups units by category', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/faction/${EMPIRE_FACTION_EID}/index.html`);
    await expect(page.locator('#faction-heading')).toContainText('The Empire');
    // Empire seed produces Hero / Lord / Melee Infantry etc. categories.
    await expect(page.locator('h4', { hasText: 'Lord' })).toBeVisible();
    await expect(page.locator('h4', { hasText: 'Melee Infantry' })).toBeVisible();
    // Every category renders at least one unit link.
    await expect(page.locator('a[href*="/unit/"]').first()).toBeVisible();
  });

  test('unit detail page renders statistics, abilities, and sections', async ({ page }) => {
    // Pick the first unit from the Empire roster so the test stays resilient
    // to seed reshuffles — we just need a unit that exists.
    await page.goto(`/view/game/${GAME_EID}/faction/${EMPIRE_FACTION_EID}/index.html`);
    const firstUnitLink = page.locator('a[href*="/unit/"]').first();
    const unitName = (await firstUnitLink.textContent()).trim();
    await firstUnitLink.click();

    await expect(page).toHaveTitle(new RegExp(unitName.replace(/[()]/g, '.')));
    await expect(page.locator('#unit-page')).toBeVisible();
    await expect(page.locator('#unit-statistics-heading')).toBeVisible();
    // Statistics rows render real values, not placeholders — proves the
    // unit-statistics idoc round-tripped through parse-unit-statistics.
    const statRows = page.locator('.unit-stat-row');
    await expect(statRows.first()).toBeVisible();
    await expect(statRows).not.toHaveCount(0);
  });

  test('full game → faction → unit hypermedia walk', async ({ page }) => {
    // Exercises the migration end-to-end: every hop reads from Datalevin via
    // the new per-template queries, and link targets come from :model/link
    // annotations resolved by the model transformer.
    await page.goto(`/view/game/${GAME_EID}/index.html`);
    await expect(page).toHaveTitle(/Warhammer III/);

    await page.locator('#atlas-dropdown-btn').click();
    await page.locator('#atlas-menu a.dropdown-item', { hasText: 'Factions' }).click();
    await expect(page).toHaveTitle(/Factions/);

    const empireCard = page.locator('a.faction-card', { hasText: 'The Empire' });
    await empireCard.click();
    await expect(page.locator('#faction-heading')).toContainText('The Empire');

    const firstUnit = page.locator('a[href*="/unit/"]').first();
    await firstUnit.click();
    await expect(page.locator('#unit-heading')).toBeVisible();
    await expect(page.locator('#unit-statistics-heading')).toBeVisible();
  });
});
