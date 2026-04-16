const { test, expect } = require('@playwright/test');

test.describe('Static page navigation', () => {
  test('dashboard loads', async ({ page }) => {
    await page.goto('/view/dashboard.html');
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

  test('root redirects to dashboard', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/view\/dashboard\.html/);
  });

  test('games dropdown opens and contains game links', async ({ page }) => {
    await page.goto('/view/dashboard.html');
    const gamesButton = page.locator('button', { hasText: 'Games' });
    await expect(gamesButton).toBeVisible();

    await gamesButton.click();
    const gameMenu = page.locator('#game-menu');
    await expect(gameMenu).toBeVisible();
    await expect(gameMenu.locator('a.dropdown-item')).toHaveCount(1);
    await expect(gameMenu.locator('a.dropdown-item')).toContainText('Total War: Warhammer III');
  });

  test('navbar shows account menu for dev user', async ({ page }) => {
    await page.goto('/view/dashboard.html');
    await expect(page.locator('button[aria-label*="Account menu"]')).toBeVisible();
  });
});
