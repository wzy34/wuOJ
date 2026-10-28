import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { users } from '@/mocks/db';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';
import { PASSWORD_CHANGED } from './SettingsPage';

async function change(current: string, next: string, confirm = next) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText(/^(Current|Temporary) password$/), current);
  await user.type(screen.getByLabelText('New password'), next);
  await user.type(screen.getByLabelText('Confirm new password'), confirm);
  await user.click(screen.getByRole('button', { name: 'Change password' }));
}

describe('SettingsPage', () => {
  it('changes the password, confirms and clears the form', async () => {
    await signIn('alice');
    renderRoute('/settings');
    expect(await screen.findByText(/There is no password recovery/)).toBeInTheDocument();

    await change('alice', 'correct horse battery');

    expect(await screen.findByText(PASSWORD_CHANGED)).toBeInTheDocument();
    expect(screen.getByLabelText('Current password')).toHaveValue('');
    expect(users.find((u) => u.username === 'alice')!.password).toBe('correct horse battery');
    expect(screen.getByRole('button', { name: 'User menu' })).toHaveTextContent('alice');
  });

  it('shows a wrong current password next to the field', async () => {
    await signIn('alice');
    renderRoute('/settings');
    await change('wrong', 'correct horse battery');
    expect(await screen.findByText('is not correct')).toBeInTheDocument();
  });

  it('holds a user with a temporary password until it is changed', async () => {
    users.find((u) => u.username === 'alice')!.mustChangePassword = true;
    await signIn('alice');
    const { router } = renderRoute('/submissions');

    expect(await screen.findByText('Choose a new password')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/settings');
    expect(screen.getByText(/An admin reset your password/)).toBeInTheDocument();

    await change('alice', 'correct horse battery');
    await waitFor(() => expect(router.state.location.pathname).toBe('/submissions'));
    expect(await screen.findByRole('heading', { name: 'Submissions' })).toBeInTheDocument();
  });

  it('keeps Sign out available while held', async () => {
    users.find((u) => u.username === 'alice')!.mustChangePassword = true;
    await signIn('alice');
    const { router } = renderRoute('/problems');
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    await user.click(await screen.findByRole('button', { name: 'User menu' }));
    await user.click(await screen.findByText('Sign out'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems'));
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument();
  });
});
