const { test, expect } = require('@playwright/test');

const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const WOOD_ELVES_FACTION_EID = 'f0000018-0000-4000-8000-000000000000';
const LAND_BATTLE_MODE_EID = 'a1b2c3d4-0001-4000-8000-000000000001';

// Seeded ancillary that grants an active ability of the same name.
const HAIL_OF_DOOM_KEY = 'wh_dlc05_anc_enchanted_item_hail_of_doom_arrow';

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

async function createDraft(page) {
  await page.goto(`/view/game/${GAME_EID}/draft/create.html`);
  await page.locator('#faction-eid').selectOption(WOOD_ELVES_FACTION_EID);
  await page.locator('#game-mode-eid').selectOption(LAND_BATTLE_MODE_EID);
  await page.locator('#create-draft-form button[type="submit"]').click();
  await expect(page.locator('.draft-page')).toBeVisible({ timeout: 10000 });
}

async function selectUnit(page, name) {
  await page.locator(`.draft-unit-card[aria-label*="${name}"]`).first().click();
  await expect(page.locator('#draft-unit .draft-stats-name')).toContainText(name, { timeout: 5000 });
}

test.describe.serial('Item-granted abilities', () => {
  test.beforeEach(async ({ context }) => {
    await addDevCookie(context);
  });

  test('selecting an item surfaces its granted abilities; deselecting hides them', async ({ page }) => {
    await createDraft(page);
    await selectUnit(page, 'Glade Lord');

    const itemAbilitiesSection = page.locator('[aria-labelledby="panel-item-abilities-heading"]');
    await expect(itemAbilitiesSection).not.toBeVisible();

    const itemCheck = page.locator(`#draft-unit-form input.draft-item-check[value="${HAIL_OF_DOOM_KEY}"]`);
    if ((await itemCheck.count()) === 0) {
      test.skip(true, 'Glade Lord has no seeded Hail of Doom Arrow; item abilities cannot be exercised.');
    }
    await itemCheck.check();

    await expect(
      itemAbilitiesSection.locator('.draft-passive-name'),
    ).toContainText(['Hail of Doom Arrow'], { timeout: 5000 });

    await page
      .locator(`#draft-unit-form input.draft-item-check[value="${HAIL_OF_DOOM_KEY}"]`)
      .uncheck();
    await expect(itemAbilitiesSection).not.toBeVisible({ timeout: 5000 });
  });

  test('item abilities persist on a placed entry', async ({ page }) => {
    await createDraft(page);
    await selectUnit(page, 'Glade Lord');

    const itemCheck = page.locator(`#draft-unit-form input.draft-item-check[value="${HAIL_OF_DOOM_KEY}"]`);
    if ((await itemCheck.count()) === 0) {
      test.skip(true, 'Glade Lord has no seeded Hail of Doom Arrow; item abilities cannot be exercised.');
    }
    await itemCheck.check();
    await expect(
      page.locator('[aria-labelledby="panel-item-abilities-heading"] .draft-passive-name'),
    ).toContainText(['Hail of Doom Arrow'], { timeout: 5000 });

    await page.locator('.draft-add-btn:not(.draft-add-btn--reinf)').click();
    const lordSlot = page.locator('#main-army-section-lord-slot');
    await expect(lordSlot).toHaveAttribute('aria-label', /Glade Lord/i, { timeout: 5000 });

    // Re-open the placed entry: the stored selection should re-render the
    // Item Abilities section from the persisted item keys.
    await lordSlot.locator('.draft-slot-card-button').click();
    await expect(page.locator('.draft-editing-indicator')).toBeVisible({ timeout: 5000 });
    await expect(
      page.locator(`#draft-unit-form input.draft-item-check[value="${HAIL_OF_DOOM_KEY}"]`),
    ).toBeChecked();
    await expect(
      page.locator('[aria-labelledby="panel-item-abilities-heading"] .draft-passive-name'),
    ).toContainText(['Hail of Doom Arrow'], { timeout: 5000 });
  });
});
