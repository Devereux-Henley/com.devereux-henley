const { test, expect } = require('@playwright/test');

test.describe('Static page navigation', () => {
  test('game selector loads', async ({ page }) => {
    await page.goto('/view/game/index.html');
    await expect(page).toHaveTitle(/RTS/);
    await expect(page.locator('nav.navbar')).toBeVisible();
    await expect(page.locator('main#content')).toBeVisible();
  });

  test('about page loads', async ({ page }) => {
    await page.goto('/view/about.html');
    await expect(page).toHaveTitle(/About/);
    await expect(page.locator('main#content')).toBeVisible();
  });

  test('contact page loads', async ({ page }) => {
    await page.goto('/view/contact.html');
    await expect(page).toHaveTitle(/Contact/);
    await expect(page.locator('main#content')).toBeVisible();
  });

  test('root redirects to game selector', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/view\/game\/index\.html/);
  });

  test('game selector lists available games', async ({ page }) => {
    await page.goto('/view/game/index.html');
    const gameCards = page.locator('a.game-card');
    await expect(gameCards.first()).toBeVisible();
    await expect(gameCards.first()).toContainText('Total War: Warhammer III');
  });

  test('navbar shows account menu for dev user', async ({ page }) => {
    await page.goto('/view/game/index.html');
    await expect(page.locator('button[aria-label*="Account menu"]')).toBeVisible();
  });
});
