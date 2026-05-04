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

async function selectArchmage(page) {
  // Canonical variant carries a lore suffix in its name now ("Archmage (High)"),
  // so match the prefix + space rather than requiring a trailing comma.
  const card = page.locator('.draft-unit-card[aria-label^="Archmage "]').first();
  await card.click();
  await expect(page.locator('#draft-unit .draft-stats-name')).toContainText('Archmage', { timeout: 5000 });
}

test.describe.serial('Lore-of-magic selection (lore-as-unit-swap)', () => {
  test.beforeEach(async ({ context }) => {
    await addDevCookie(context);
  });

  test('roster shows a single consolidated Archmage card backed by the canonical variant', async ({ page }) => {
    await createHighElvesDraft(page);
    const cards = page.locator('.draft-unit-card[aria-label^="Archmage"]');
    await expect(cards).toHaveCount(1);
  });

  test('preview panel renders a Lore dropdown that swaps unit-eid (no marks for High Elves)', async ({ page }) => {
    await createHighElvesDraft(page);
    await selectArchmage(page);

    // No marks on High Elves wizards → mark selector is absent.
    await expect(page.locator('#draft-mark-select')).toHaveCount(0);

    const loreSelect = page.locator('#draft-lore-select');
    await expect(loreSelect).toBeVisible();
    await expect(loreSelect).toHaveAttribute('name', 'unit-eid');

    // Each option carries a variant unit-eid; one option per available lore.
    const options = loreSelect.locator('option');
    const count   = await options.count();
    expect(count).toBeGreaterThan(1);
  });

  test('toggling lore swaps unit-eid and re-renders the panel with the new variant', async ({ page }) => {
    await createHighElvesDraft(page);
    await selectArchmage(page);

    const loreSelect = page.locator('#draft-lore-select');
    const options    = loreSelect.locator('option');
    const initial    = await loreSelect.inputValue();

    // Pick a different lore variant.
    const altEid = await options.nth(1).getAttribute('value');
    expect(altEid).not.toEqual(initial);
    await loreSelect.selectOption(altEid);

    // Panel re-renders with the new variant — header name + portrait reflect it.
    await expect(page.locator('.draft-stats-portrait')).toHaveAttribute(
      'src', new RegExp(altEid), { timeout: 5000 });
    await expect(loreSelect).toHaveValue(altEid);
  });
});
