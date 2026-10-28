import { expect, test } from '@playwright/test';

test('a visitor writes code, registers and submits it', async ({ page }) => {
  // Unique, so the test can run again against a backend that keeps its accounts.
  const username = `e2e-${Date.now().toString(36)}`;
  const code = 'int main() { return 0; } // @mock-verdict: WA';

  await page.goto('/problems/a-plus-b');
  await expect(page.getByRole('heading', { name: '1001. A + B' })).toBeVisible();
  const editor = page.locator('.monaco-editor .view-lines');
  await expect(editor).toContainText('#include <bits/stdc++.h>');
  await editor.click();
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.type(code);

  await page.getByRole('button', { name: 'Sign in to submit' }).click();
  await page.getByRole('link', { name: 'Create an account' }).click();
  await expect(page).toHaveURL(/\/register\?redirect=%2Fproblems%2Fa-plus-b$/);
  // The login form has the same labels; wait until the register form has replaced it.
  await expect(page.getByRole('button', { name: 'Create account' })).toBeVisible();
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password', { exact: true }).fill('correct horse battery');
  await page.getByLabel('Confirm password').fill('correct horse battery');
  await page.getByRole('button', { name: 'Create account' }).click();

  await expect(page).toHaveURL(/\/problems\/a-plus-b$/);
  await expect(page.getByRole('button', { name: 'User menu' })).toContainText(username);
  await expect(editor).toContainText('@mock-verdict: WA');
  await page.getByRole('button', { name: 'Submit' }).click();

  const result = page.getByRole('region', { name: 'Submission result' });
  await expect(result.getByText('Wrong Answer')).toBeVisible({ timeout: 20_000 });
});
