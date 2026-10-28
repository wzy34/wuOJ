import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { apiPatch, signIn } from '@/test/api';
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

  it('moves between the admin pages with tabs', async () => {
    await signIn('admin');
    const { router } = renderRoute('/admin/users');
    await userEvent.click(await screen.findByRole('tab', { name: 'Audit log' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/audit'));
    expect(await screen.findByText('No admin actions yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('tab', { name: 'Accounts' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/users'));
  });
});
