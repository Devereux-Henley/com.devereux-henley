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
  return new URL(page.url()).pathname;
}

async function selectUnit(page, name) {
  await page
    .locator(`.draft-unit-card[aria-label*="${name}"]`)
    .first()
    .click();
  await expect(page.locator('#draft-unit .draft-stats-name')).toContainText(name, {
    timeout: 5000,
  });
}

async function clickAddToMain(page) {
  const btn = page.locator('.draft-add-btn:not(.draft-add-btn--reinf)');
  await btn.click();
  await expect(btn).not.toHaveClass(/htmx-request/, { timeout: 10000 });
}

test.describe.serial('Full draft user flow', () => {
  test.beforeEach(async ({ context }) => {
    await addDevCookie(context);
  });

  test('toggling an item on a placed unit persists across reload', async ({ page }) => {
    // Balthasar Gelt is a Lord with 5 items in the Empire seed — a good
    // anchor for exercising the item-checkbox toggle path that wasn't
    // covered by the existing draft specs.
    const draftPath = await createDraft(page);
    await selectUnit(page, 'Balthasar Gelt');
    await clickAddToMain(page);

    const lordSlot = page.locator('#main-army-section-lord-slot');
    await expect(lordSlot).toHaveAttribute('aria-label', /Balthasar Gelt/i, {
      timeout: 5000,
    });

    // Open the placed entry's panel via the slot's card button.
    await lordSlot.locator('.draft-slot-card-button').click();
    await expect(page.locator('.draft-editing-indicator')).toBeVisible({
      timeout: 5000,
    });

    // Toggle the first item. The wrapping form patches on change with a
    // 300ms debounce; wait for the resulting request so we know the
    // mutation actually hit the server before we reload.
    const firstItem = page.locator('.draft-item-check').first();
    const itemKey = await firstItem.getAttribute('value');
    const responsePromise = page.waitForResponse(
      (res) => res.url().includes('/actions/draft/') && res.url().includes('/entry/'),
    );
    await firstItem.check();
    await responsePromise;

    // Hard reload from the draft URL to confirm the toggle survives —
    // proves the value made it through `update-entry!` and is being
    // hydrated by the next `unit-by-eid` pull.
    await page.goto(draftPath);
    await lordSlot.locator('.draft-slot-card-button').click();
    await expect(page.locator('.draft-editing-indicator')).toBeVisible({
      timeout: 5000,
    });
    const reloaded = page.locator(`.draft-item-check[value="${itemKey}"]`);
    await expect(reloaded).toBeChecked({ timeout: 5000 });
  });

  test('create → add → reload → my-drafts navigation walks the whole arc', async ({ page }) => {
    // Single integrated walk: create a draft, add a lord + an infantry
    // unit, hard-reload to confirm persistence, then navigate to the
    // my-drafts list and follow the link back. Anchors that every step
    // of the draft surface (create form submit → HTMX add → re-pull →
    // my-drafts query → link round-trip) survives the datalog cutover.
    const draftPath = await createDraft(page);

    // Add a lord + an infantry unit so the draft has detectable state.
    await selectUnit(page, 'Arch Lector');
    await clickAddToMain(page);
    await expect(page.locator('#main-army-section-lord-slot')).toHaveAttribute(
      'aria-label',
      /Arch Lector/i,
      { timeout: 5000 },
    );

    await selectUnit(page, 'Swordsmen');
    await clickAddToMain(page);
    // `#main-army-section-slots` contains both the lord slot and the
    // regular slots; the lord is its own dedicated id, so count the
    // non-lord filled slots to confirm a single infantry add.
    const nonLordFilled = page.locator(
      '#main-army-section-slots .draft-slot--filled:not(#main-army-section-lord-slot)',
    );
    await expect(nonLordFilled).toHaveCount(1, { timeout: 5000 });

    // Reload the draft index — the entries should re-pull and re-render
    // identical. Proves the new `draft-state-by-eid` returns the same
    // shape across freshly-loaded vs in-memory state.
    await page.reload();
    await expect(page.locator('#main-army-section-lord-slot')).toHaveAttribute(
      'aria-label',
      /Arch Lector/i,
    );
    await expect(nonLordFilled).toHaveCount(1);

    // Navigate to my-drafts; the draft should be linked back to the
    // path we started at.
    await page.goto(`/view/game/${GAME_EID}/draft/me.html`);
    await expect(page.locator(`a[href="${draftPath}"]`)).toBeVisible();
  });
});
