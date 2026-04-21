const { test, expect } = require('@playwright/test');

const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const HIGH_ELVES_FACTION_EID = 'f000000a-0000-4000-8000-000000000000';
const ARCHMAGE_EID = '000a0004-0000-4000-8000-000000000000';
const LAND_BATTLE_MODE_EID = 'a1b2c3d4-0001-4000-8000-000000000001';
const FIRE_LORE_KEY = 'wh_main_lore_fire';
const LIFE_LORE_KEY = 'wh_dlc05_lore_life';

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

async function selectArchmage(page) {
  const card = page.locator('.draft-unit-card[aria-label^="Archmage,"]').first();
  await card.click();
  await expect(page.locator('#draft-unit .draft-stats-name')).toContainText('Archmage', { timeout: 5000 });
}

async function addToMain(page) {
  const btn = page.locator('.draft-add-btn:not(.draft-add-btn--reinf)');
  await btn.click();
  await expect(btn).not.toHaveClass(/htmx-request/, { timeout: 10000 });
}

test.describe.serial('Lore-of-magic selection', () => {
  test.beforeEach(async ({ context }) => {
    await addDevCookie(context);
  });

  test('roster shows a single consolidated Archmage card (no per-lore siblings)', async ({ page }) => {
    await createHighElvesDraft(page);
    const cards = page.locator('.draft-unit-card[aria-label^="Archmage"]');
    await expect(cards).toHaveCount(1);
    await expect(cards.first()).toHaveAttribute('aria-label', /Archmage,/);
  });

  test('preview panel renders 9 lore radios and no spells when no lore selected', async ({ page }) => {
    await createHighElvesDraft(page);
    await selectArchmage(page);

    const loreRadios = page.locator('#draft-unit-form input[name="lore"]');
    await expect(loreRadios).toHaveCount(9);
    await expect(loreRadios.filter({ has: page.locator(':checked') })).toHaveCount(0);
    await expect(page.locator('#draft-unit-form input[name="spells"]')).toHaveCount(0);
  });

  test('selecting Fire lore swaps portrait + spell pool', async ({ page }) => {
    await createHighElvesDraft(page);
    await selectArchmage(page);

    await page.locator(`#draft-unit-form input[name="lore"][value="${FIRE_LORE_KEY}"]`).check();

    // Wait for the HTMX swap to land: 6 spell checkboxes appear + portrait swaps.
    await expect(page.locator('#draft-unit-form input[name="spells"]')).toHaveCount(6, { timeout: 5000 });
    await expect(page.locator(`#draft-unit-form input[name="lore"][value="${FIRE_LORE_KEY}"]`))
      .toBeChecked();
    await expect(page.locator('.draft-stats-portrait')).toHaveAttribute('src', /000a0006/);
  });

  test('changing lore clears previously-selected spells', async ({ page }) => {
    await createHighElvesDraft(page);
    await selectArchmage(page);

    await page.locator(`#draft-unit-form input[name="lore"][value="${FIRE_LORE_KEY}"]`).check();
    await expect(page.locator(`#draft-unit-form input[name="lore"][value="${FIRE_LORE_KEY}"]`))
      .toBeChecked({ timeout: 5000 });

    // Pick two Fire spells.
    const fireCheckboxes = page.locator('#draft-unit-form input[name="spells"]');
    await expect(fireCheckboxes).toHaveCount(6);
    await fireCheckboxes.nth(0).check();
    await fireCheckboxes.nth(1).check();
    await expect(page.locator('#draft-unit-form input[name="spells"]:checked')).toHaveCount(2);

    // Switch to Life lore — the preview GET serves the new pool without
    // persisted state, and no spells should carry over.
    await page.locator(`#draft-unit-form input[name="lore"][value="${LIFE_LORE_KEY}"]`).check();
    await expect(page.locator(`#draft-unit-form input[name="lore"][value="${LIFE_LORE_KEY}"]`))
      .toBeChecked({ timeout: 5000 });
    await expect(page.locator('#draft-unit-form input[name="spells"]:checked')).toHaveCount(0);

    const portraitSrc = await page.locator('.draft-stats-portrait').getAttribute('src');
    expect(portraitSrc).toContain('000a0009');
  });

  test('add-to-main persists the selected lore, and edit-mode reopens with it checked', async ({ page }) => {
    await createHighElvesDraft(page);
    await selectArchmage(page);
    await page.locator(`#draft-unit-form input[name="lore"][value="${FIRE_LORE_KEY}"]`).check();
    await expect(page.locator(`#draft-unit-form input[name="lore"][value="${FIRE_LORE_KEY}"]`))
      .toBeChecked({ timeout: 5000 });

    await addToMain(page);

    const lordSlot = page.locator('#main-army-section-lord-slot');
    await expect(lordSlot).toHaveAttribute('aria-label', /Archmage/i, { timeout: 5000 });
    await lordSlot.locator('.draft-slot-card-button').click();

    await expect(page.locator('.draft-editing-indicator')).toBeVisible({ timeout: 5000 });
    await expect(page.locator(`#draft-unit-form input[name="lore"][value="${FIRE_LORE_KEY}"]`))
      .toBeChecked({ timeout: 5000 });
  });

  test('unit page with ?lore= renders the lore-specific spell table and portrait', async ({ page }) => {
    await page.goto(`/view/game/${GAME_EID}/unit/${ARCHMAGE_EID}/index.html?lore=${FIRE_LORE_KEY}`);
    await expect(page.locator(`input[name="lore"][value="${FIRE_LORE_KEY}"]`)).toBeChecked();

    const portraitSrc = await page.locator('.unit-hero-card').getAttribute('src');
    expect(portraitSrc).toContain('000a0006');

    const spellRows = page.locator('#unit-spells tbody tr');
    await expect(spellRows).toHaveCount(6);
  });
});
