const { test, expect } = require('@playwright/test');

const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const EMPIRE_FACTION_EID = '35dd38fa-2bcc-4492-8f58-a106d0d02cbb';
const DOMINATION_MODE_EID = 'a1b2c3d4-0002-4000-8000-000000000002';
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

async function createDraft(page, gameModeEid) {
  await page.goto(`/view/game/${GAME_EID}/draft/create.html`);
  await page.locator('#faction-eid').selectOption(EMPIRE_FACTION_EID);
  await page.locator('#game-mode-eid').selectOption(gameModeEid);

  await page.locator('#create-draft-form button[type="submit"]').click();
  await expect(page.locator('.draft-page')).toBeVisible({ timeout: 10000 });
}

async function selectUnit(page, name) {
  const card = page.locator(`.draft-unit-card[aria-label*="${name}"]`).first();
  await card.click();
  await expect(page.locator('#draft-unit .draft-stats-name')).toContainText(name, { timeout: 5000 });
}

async function clickAddToMain(page) {
  const btn = page.locator('.draft-add-btn:not(.draft-add-btn--reinf)');
  await btn.click();
  await expect(btn).not.toHaveClass(/htmx-request/, { timeout: 10000 });
}

async function clickAddToReinforcements(page) {
  const btn = page.locator('.draft-add-btn--reinf');
  await btn.click();
  await expect(btn).not.toHaveClass(/htmx-request/, { timeout: 10000 });
}

test.describe.serial('Draft unit operations', () => {
  test.beforeEach(async ({ context }) => {
    await addDevCookie(context);
  });

  test('select a unit from the roster', async ({ page }) => {
    await createDraft(page, DOMINATION_MODE_EID);

    const card = page.locator('.draft-unit-card').first();
    await card.click();

    await expect(page.locator('#draft-unit')).toBeVisible();
    await expect(page.locator('#draft-unit')).not.toHaveAttribute('hidden', '');
    await expect(page.locator('.draft-stats-name')).toBeVisible();
  });

  test('add a lord to the main army section', async ({ page }) => {
    await createDraft(page, LAND_BATTLE_MODE_EID);

    const lordSlot = page.locator('#main-army-section-lord-slot');
    await expect(lordSlot).toHaveAttribute('aria-label', /empty/i);

    await selectUnit(page, 'Arch Lector');
    await clickAddToMain(page);

    await expect(lordSlot).toHaveAttribute('aria-label', /Arch Lector/i, { timeout: 5000 });
  });

  test('add an infantry unit to the main army section', async ({ page }) => {
    await createDraft(page, LAND_BATTLE_MODE_EID);

    const mainSlots = page.locator('#main-army-section-slots');
    await expect(mainSlots.locator('.draft-slot--filled')).toHaveCount(0);

    await selectUnit(page, 'Swordsmen');
    await clickAddToMain(page);

    await expect(mainSlots.locator('.draft-slot--filled')).toHaveCount(1, { timeout: 5000 });
  });

  test('add a unit to the reinforcements section', async ({ page }) => {
    await createDraft(page, DOMINATION_MODE_EID);

    await expect(page.locator('#reinforcements-section')).toBeVisible();

    await selectUnit(page, 'Swordsmen');
    await clickAddToReinforcements(page);

    const reinfSlots = page.locator('#reinforcements-section-slots');
    await expect(reinfSlots.locator('.draft-slot--filled')).toHaveCount(1, { timeout: 5000 });
  });

  test('update a placed unit selections', async ({ page }) => {
    await createDraft(page, LAND_BATTLE_MODE_EID);

    await selectUnit(page, 'Balthasar Gelt');
    await clickAddToMain(page);

    const lordSlot = page.locator('#main-army-section-lord-slot');
    await expect(lordSlot).toHaveAttribute('aria-label', /Balthasar Gelt/i, { timeout: 5000 });

    await lordSlot.locator('.draft-slot-card-button').click();
    await expect(page.locator('.draft-editing-hint')).toBeVisible({ timeout: 5000 });

    const checkbox = page.locator('.draft-spell-check, .draft-ability-check, .draft-item-check').first();
    if (await checkbox.count() > 0) {
      const costBefore = await lordSlot.locator('.draft-slot-cost').textContent();
      await checkbox.check();
      await page.waitForTimeout(800);

      const costAfter = await lordSlot.locator('.draft-slot-cost').textContent();
      expect(costAfter).not.toBe(costBefore);
    }
  });

  test('remove a unit from the draft', async ({ page }) => {
    await createDraft(page, LAND_BATTLE_MODE_EID);

    await selectUnit(page, 'Swordsmen');
    await clickAddToMain(page);

    const mainSlots = page.locator('#main-army-section-slots');
    await expect(mainSlots.locator('.draft-slot--filled')).toHaveCount(1, { timeout: 5000 });

    const slot = mainSlots.locator('.draft-slot--filled').first();
    await slot.hover();
    await slot.locator('.draft-slot-remove').click();
    await expect(mainSlots.locator('.draft-slot--filled')).toHaveCount(0, { timeout: 5000 });
  });

  test('lord cap prevents adding a second lord', async ({ page }) => {
    await createDraft(page, LAND_BATTLE_MODE_EID);

    await selectUnit(page, 'Arch Lector');
    await clickAddToMain(page);

    const lordSlot = page.locator('#main-army-section-lord-slot');
    await expect(lordSlot).toHaveAttribute('aria-label', /Arch Lector/i, { timeout: 5000 });

    await selectUnit(page, 'General of the Empire');
    await clickAddToMain(page);

    const errorAlert = page.locator('#draft-action-error');
    await expect(errorAlert).toBeVisible({ timeout: 5000 });
    await expect(errorAlert).toContainText(/lord/i);
  });

  test('per-unit cap prevents adding more than 4 copies', async ({ page }) => {
    await createDraft(page, LAND_BATTLE_MODE_EID);

    for (let i = 0; i < 4; i++) {
      await selectUnit(page, 'Swordsmen');
      await clickAddToMain(page);
    }

    const mainSlots = page.locator('#main-army-section-slots');
    await expect(mainSlots.locator('.draft-slot--filled')).toHaveCount(4, { timeout: 5000 });

    await selectUnit(page, 'Swordsmen');
    await clickAddToMain(page);

    const errorAlert = page.locator('#draft-action-error');
    await expect(errorAlert).toBeVisible({ timeout: 5000 });
    await expect(errorAlert).toContainText(/4 copies/i);
  });

  test('budget updates reflect added unit costs', async ({ page }) => {
    await createDraft(page, LAND_BATTLE_MODE_EID);

    const budgetUsed = page.locator('#main-army-section-budget .draft-budget-used');
    await expect(budgetUsed).toHaveText('0');

    await selectUnit(page, 'Swordsmen');
    await clickAddToMain(page);

    await expect(budgetUsed).not.toHaveText('0', { timeout: 5000 });
  });
});
