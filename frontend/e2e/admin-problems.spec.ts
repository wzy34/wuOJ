import { expect, test } from '@playwright/test';

// Mock problems live in memory, so the test moves between pages in the app rather than reloading.
test('an admin edits a problem and its tests, and sees both changes', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Username').fill('admin');
  await page.getByLabel('Password').fill('admin');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Problems' })).toBeVisible();

  await page.getByRole('link', { name: 'A + B' }).click();
  await page.getByRole('link', { name: 'Edit' }).click();
  await expect(page.getByRole('heading', { name: '1001. A + B' })).toBeVisible();

  const title = page.getByRole('textbox', { name: 'Title' });
  await title.fill('A + B (revised)');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Saved.')).toBeVisible();

  await page.getByRole('tab', { name: 'Tests' }).click();
  await page.getByLabel('Replace output of test 4').setInputFiles({
    name: '4.out',
    mimeType: 'text/plain',
    buffer: Buffer.from('fixed\n'),
  });
  await expect(page.getByText('4.out (6 B)')).toBeVisible();
  await page.getByRole('button', { name: 'Save tests' }).click();
  await expect(page.getByText('Saved 5 tests as a new version.')).toBeVisible();

  await page.getByRole('link', { name: 'wuOJ' }).click();
  await expect(page.getByRole('link', { name: 'A + B (revised)' })).toBeVisible();

  await page.getByRole('link', { name: 'A + B (revised)' }).click();
  await page.getByRole('link', { name: 'Edit' }).click();
  await page.getByRole('tab', { name: 'Audit log' }).click();
  const rows = page.locator('tbody tr');
  await expect(rows.nth(0)).toContainText('Saved tests (5 → 5)');
  await expect(rows.nth(0)).toContainText('1001. A + B (revised)');
  await expect(rows.nth(1)).toContainText('Edited the problem');
});
