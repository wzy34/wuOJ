import { expect, test } from '@playwright/test';

test('a student signs in, submits code and follows the verdict to the details', async ({
  page,
}) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login/);

  await page.getByLabel('Username').fill('student');
  await page.getByLabel('Password').fill('student');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Problems' })).toBeVisible();

  await page.getByRole('link', { name: 'A + B' }).click();
  await expect(page.getByRole('heading', { name: '1001. A + B' })).toBeVisible();

  const editor = page.locator('.monaco-editor .view-lines');
  await expect(editor).toContainText('#include <bits/stdc++.h>');
  await editor.click();
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.type('int main() { return 0; } // @mock-verdict: WA');
  await page.getByRole('button', { name: 'Submit' }).click();

  const result = page.getByRole('region', { name: 'Submission result' });
  await expect(result).toContainText(/Submission #\d+/);
  const id = /Submission #(\d+)/.exec((await result.textContent()) ?? '')?.[1];
  expect(id).toBeDefined();
  await expect(result.getByText('Wrong Answer')).toBeVisible({ timeout: 20_000 });

  await result.getByRole('link', { name: 'View details' }).click();
  await expect(page).toHaveURL(new RegExp(`/submissions/${id}$`));
  await expect(page.getByRole('heading', { name: `Submission #${id}` })).toBeVisible();

  const testResults = page.locator('.arco-card', { hasText: 'Test results' });
  const rows = testResults.locator('tbody tr');
  await expect(rows).toHaveCount(3);
  await expect(rows.nth(2)).toContainText('Wrong Answer');
  await expect(rows.nth(0)).toContainText('Accepted');
});
