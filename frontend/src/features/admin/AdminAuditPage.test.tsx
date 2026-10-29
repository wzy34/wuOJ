import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { problems } from '@/mocks/db';
import { apiForm, apiPatch, signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

describe('AdminAuditPage', () => {
  it('lists admin actions newest first with readable descriptions', async () => {
    await signIn('admin');
    await apiPatch('/api/admin/users/2', { role: 'ADMIN' });
    await apiPatch('/api/admin/users/3', { enabled: false });
    renderRoute('/admin/audit');

    expect(await screen.findByText('Disabled the account')).toBeInTheDocument();
    const rows = screen.getAllByRole('row').slice(1);
    expect(within(rows[0]).getByRole('link', { name: 'bob' })).toHaveAttribute(
      'href',
      '/users/bob',
    );
    expect(within(rows[0]).getByRole('link', { name: 'admin' })).toBeInTheDocument();
    expect(within(rows[1]).getByText('Changed role from User to Admin')).toBeInTheDocument();
  });

  it('describes problem actions and links to the editor', async () => {
    await signIn('admin');
    const base = problems.find((p) => p.slug === 'a-plus-b')!.testdataVersion;
    const keepAll = [1, 2, 3, 4, 5].map((n) => ({ input: { keep: n }, output: { keep: n } }));
    await apiForm(
      '/api/admin/problems/1/tests',
      {
        manifest: {
          baseVersion: base,
          tests: [...keepAll, { input: { upload: 0 }, output: { upload: 1 } }],
        },
      },
      ['6', '7'],
    );
    renderRoute('/admin/audit');

    expect(await screen.findByText('Saved tests (5 → 6)')).toBeInTheDocument();
    const row = within(screen.getAllByRole('row')[1]);
    expect(row.getByRole('link', { name: '1001. A + B' })).toHaveAttribute(
      'href',
      '/admin/problems/1',
    );
    expect(row.getByRole('link', { name: 'admin' })).toBeInTheDocument();
  });

  it('moves between the admin pages with tabs', async () => {
    await signIn('admin');
    const { router } = renderRoute('/admin/users');
    await userEvent.click(await screen.findByRole('tab', { name: 'Audit log' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/audit'));
    expect(await screen.findByText('No admin actions yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Problems' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/problems'));
    await userEvent.click(await screen.findByRole('tab', { name: 'Accounts' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/users'));
  });
});
