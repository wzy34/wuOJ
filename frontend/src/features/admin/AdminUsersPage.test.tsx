import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { users } from '@/mocks/db';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';
import { OWN_ACCOUNT } from './AdminUsersPage';

function row(username: string) {
  return within(screen.getByRole('link', { name: username }).closest('tr')!);
}

/** Popconfirm buttons animate in; jsdom never finishes the animation. */
const user = () => userEvent.setup({ pointerEventsCheck: 0 });

async function confirm(username: string, action: string) {
  const u = user();
  await u.click(row(username).getByRole('button', { name: action }));
  // The confirmation is a popup without a dialog role; its OK button repeats the action's name.
  const popup = await waitFor(() => {
    const found = document.querySelector<HTMLElement>('.arco-popconfirm');
    expect(found).not.toBeNull();
    return found!;
  });
  expect(popup).toHaveTextContent(username);
  expect(popup).toHaveTextContent('They will be signed out everywhere.');
  await u.click(within(popup).getByRole('button', { name: action }));
}

describe('AdminUsersPage', () => {
  it('lists accounts newest first with role, status and sign-in time', async () => {
    users.find((u) => u.username === 'bob')!.enabled = false;
    await signIn('admin');
    renderRoute('/admin/users');
    await screen.findByRole('link', { name: 'bob' });
    const names = screen
      .getAllByRole('row')
      .slice(1)
      .map((r) => within(r).getAllByRole('cell')[0].textContent);
    expect(names).toEqual(['admin', 'bob', 'alice', 'student']);
    expect(row('bob').getByText('Disabled')).toBeInTheDocument();
    expect(row('alice').getByText('Active')).toBeInTheDocument();
    expect(row('alice').getByText('Never')).toBeInTheDocument();
    expect(row('admin').getByText('Admin')).toBeInTheDocument();
    expect(row('alice').getByRole('link', { name: 'alice' })).toHaveAttribute(
      'href',
      '/users/alice',
    );
  });

  it('searches by username once typing pauses and keeps the search in the URL', async () => {
    await signIn('admin');
    const { router } = renderRoute('/admin/users?page=1');
    await screen.findByRole('link', { name: 'bob' });
    await userEvent.type(screen.getByLabelText('Search accounts by username'), 'bo');
    await waitFor(() => expect(router.state.location.search).toBe('?q=bo'));
    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'alice' })).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('link', { name: 'bob' })).toBeInTheDocument();
  });

  it('filters disabled accounts', async () => {
    users.find((u) => u.username === 'bob')!.enabled = false;
    await signIn('admin');
    const { router } = renderRoute('/admin/users?enabled=false');
    await screen.findByRole('link', { name: 'bob' });
    expect(screen.queryByRole('link', { name: 'alice' })).not.toBeInTheDocument();
    expect(router.state.location.search).toBe('?enabled=false');
  });

  it('disables and enables an account after confirmation', async () => {
    await signIn('admin');
    renderRoute('/admin/users');
    await screen.findByRole('link', { name: 'bob' });

    await confirm('bob', 'Disable');
    await waitFor(() => expect(row('bob').getByText('Disabled')).toBeInTheDocument());
    expect(users.find((u) => u.username === 'bob')!.enabled).toBe(false);
    expect(row('bob').getByRole('button', { name: 'Enable' })).toBeInTheDocument();

    await confirm('bob', 'Enable');
    await waitFor(() => expect(row('bob').getByText('Active')).toBeInTheDocument());
  });

  it('changes a role after confirmation', async () => {
    await signIn('admin');
    renderRoute('/admin/users');
    await screen.findByRole('link', { name: 'alice' });
    await confirm('alice', 'Make admin');
    await waitFor(() => expect(row('alice').getByText('Admin')).toBeInTheDocument());
    expect(row('alice').getByRole('button', { name: 'Make user' })).toBeInTheDocument();
  });

  it('offers nothing on the own row', async () => {
    await signIn('admin');
    renderRoute('/admin/users');
    await screen.findByRole('link', { name: 'admin' });
    for (const action of ['Make user', 'Disable', 'Reset password']) {
      expect(row('admin').getByRole('button', { name: action })).toBeDisabled();
    }
    await user().hover(row('admin').getByRole('button', { name: 'Disable' }).parentElement!);
    expect(await screen.findByText(OWN_ACCOUNT)).toBeInTheDocument();
  });

  it('shows a temporary password once', async () => {
    await signIn('admin');
    renderRoute('/admin/users');
    await screen.findByRole('link', { name: 'bob' });

    await confirm('bob', 'Reset password');

    const dialog = await screen.findByRole('dialog', { name: 'Temporary password for bob' });
    const password = users.find((u) => u.username === 'bob')!.password;
    expect(within(dialog).getByLabelText('Temporary password')).toHaveTextContent(password);
    expect(within(dialog).getByText('This password will not be shown again.')).toBeInTheDocument();
    expect(
      within(dialog).getByRole('button', { name: 'Copy temporary password' }),
    ).toBeInTheDocument();

    await user().click(within(dialog).getByRole('button', { name: 'Done' }));
    await waitFor(() => expect(document.body).not.toHaveTextContent(password));
    await waitFor(() => expect(row('bob').getByText('Must change password')).toBeInTheDocument());
  });
});
