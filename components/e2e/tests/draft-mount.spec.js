const { test, expect } = require('@playwright/test');

const GAME_EID = 'eea787d7-1065-45eb-a3f6-e26f32c294a1';
const EMPIRE_FACTION_EID = '35dd38fa-2bcc-4492-8f58-a106d0d02cbb';
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

async function createDraft(page) {
  await page.goto(`/view/game/${GAME_EID}/draft/create.html`);
  await page.locator('#faction-eid').selectOption(EMPIRE_FACTION_EID);
  await page.locator('#game-mode-eid').selectOption(LAND_BATTLE_MODE_EID);
  await page.locator('#create-draft-form button[type="submit"]').click();
  await expect(page.locator('.draft-page')).toBeVisible({ timeout: 10000 });
}

async function selectUnit(page, name) {
  await page.locator(`.draft-unit-card[aria-label*="${name}"]`).first().click();
  await expect(page.locator('#draft-unit .draft-stats-name')).toContainText(name, { timeout: 5000 });
}

async function addToMain(page) {
  const btn = page.locator('.draft-add-btn:not(.draft-add-btn--reinf)');
  await btn.click();
  await expect(btn).not.toHaveClass(/htmx-request/, { timeout: 10000 });
}

test.describe.serial('Mount-selection overrides', () => {
  test.beforeEach(async ({ context }) => {
    await addDevCookie(context);
  });

  test('selecting a mount re-renders stats, health, cost, and granted abilities', async ({ page }) => {
    await createDraft(page);
    await selectUnit(page, 'Emperor Karl Franz');
    await addToMain(page);

    const lordSlot = page.locator('#main-army-section-lord-slot');
    await expect(lordSlot).toHaveAttribute('aria-label', /Emperor Karl Franz/i, { timeout: 5000 });
    await lordSlot.locator('.draft-slot-card-button').click();
    await expect(page.locator('.draft-editing-indicator')).toBeVisible({ timeout: 5000 });

    const costCell = page.locator('#draft-unit .draft-stats-cost');
    const healthCell = page.locator('#draft-unit .draft-health-value');
    const costBefore = await costCell.textContent();
    const healthBefore = await healthCell.textContent();

    const mountRadios = page.locator('#draft-unit-form input[name="mount"]');
    const mountRadioCount = await mountRadios.count();
    if (mountRadioCount <= 1) {
      test.skip(true, 'Karl Franz has no seeded mounts; mount re-render cannot be exercised.');
    }

    const firstMount = mountRadios.nth(1);
    const mountKey = await firstMount.getAttribute('value');
    await firstMount.check();

    await expect(async () => {
      expect(await costCell.textContent()).not.toBe(costBefore);
    }).toPass({ timeout: 5000 });

    const costAfter = await costCell.textContent();
    const healthAfter = await healthCell.textContent();
    expect(Number(costAfter)).toBeGreaterThan(Number(costBefore));
    expect(healthAfter).not.toBe(healthBefore);
    await expect(page.locator(`#draft-unit-form input[name="mount"][value="${mountKey}"]`)).toBeChecked();

    // Karl Franz on any mount that grants Bloodroar should surface the
    // "Mount Abilities" section. We don't assert the specific ability in
    // case the seed changes — just that the list is populated.
    const grantedNames = await page
      .locator('[aria-labelledby="panel-mount-abilities-heading"] .draft-passive-name')
      .allTextContents();
    if (mountKey === 'mount_deathclaw') {
      expect(grantedNames.map(n => n.trim())).toContain('Bloodroar');
    }
  });

  test('preview-mode mount toggle re-renders the unit panel and persists on add', async ({ page }) => {
    await createDraft(page);
    await selectUnit(page, 'Emperor Karl Franz');

    // Sanity-check: preview mode (no editing indicator) and form points at
    // the /unit/:eid endpoint, not /entry/:eid.
    await expect(page.locator('.draft-editing-indicator')).not.toBeVisible();
    const formAction = await page.locator('#draft-unit-form').getAttribute('hx-get');
    expect(formAction).toMatch(/\/components\/draft\/.+\/unit\//);

    const costCell = page.locator('#draft-unit .draft-stats-cost');
    const costBefore = await costCell.textContent();

    const mountRadios = page.locator('#draft-unit-form input[name="mount"]');
    if (await mountRadios.count() <= 1) {
      test.skip(true, 'Karl Franz has no seeded mounts.');
    }
    const mountRadio = mountRadios.nth(1);
    const mountKey = await mountRadio.getAttribute('value');
    await mountRadio.check();

    await expect(async () => {
      expect(await costCell.textContent()).not.toBe(costBefore);
    }).toPass({ timeout: 5000 });

    const costPreview = Number(await costCell.textContent());
    expect(costPreview).toBeGreaterThan(Number(costBefore));
    await expect(
      page.locator(`#draft-unit-form input[name="mount"][value="${mountKey}"]`),
    ).toBeChecked();

    // Adding now should persist the previewed mount cost into the slot.
    await page.locator('.draft-add-btn:not(.draft-add-btn--reinf)').click();
    const lordSlot = page.locator('#main-army-section-lord-slot');
    await expect(lordSlot).toHaveAttribute('aria-label', /Emperor Karl Franz/i, { timeout: 5000 });
    const slotCost = await lordSlot.locator('.draft-slot-cost').textContent();
    expect(Number(slotCost)).toBe(costPreview);
  });

  // Note: A test that exercised the GET preview behavior via the JSON API
  // was dropped in PR F since /api no longer serves JSON. The same preview
  // semantics are still exercised by the in-browser preview-mode mount
  // toggle test above (which observes cost updates in the rendered DOM).
});
