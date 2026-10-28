import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { users } from '@/mocks/db';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

describe('ProfilePage', () => {
  it("shows a user's progress to visitors", async () => {
    renderRoute('/users/student');
    expect(await screen.findByRole('heading', { name: 'student' })).toBeInTheDocument();
    expect(screen.getByText(/^Joined \d{4}-\d{2}-\d{2}$/)).toBeInTheDocument();
    expect(screen.getByText('Solved')).toBeInTheDocument();
    const solved = screen.getByRole('list', { name: 'Solved problems' });
    expect(solved).toHaveTextContent('1001. A + B');
    expect(screen.getByRole('link', { name: '1001. A + B' })).toHaveAttribute(
      'href',
      '/problems/a-plus-b',
    );
    await waitFor(() => expect(document.title).toBe('student · wuOJ'));
  });

  it("links to the user's submissions", async () => {
    const { router } = renderRoute('/users/alice');
    await userEvent.click(await screen.findByRole('link', { name: 'View submissions' }));
    expect(router.state.location.pathname).toBe('/submissions');
    expect(router.state.location.search).toBe('?scope=all&user=alice');
  });

  it('marks admins and says when nothing is solved yet', async () => {
    renderRoute('/users/admin');
    expect(await screen.findByText('Admin')).toBeInTheDocument();
    expect(screen.getByText('No problems solved yet')).toBeInTheDocument();
  });

  it('opens the own profile from the user menu', async () => {
    await signIn('alice');
    const { router } = renderRoute('/problems');
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    await user.click(await screen.findByRole('button', { name: 'User menu' }));
    await user.click(await screen.findByText('Profile'));
    expect(router.state.location.pathname).toBe('/users/alice');
    expect(await screen.findByRole('heading', { name: 'alice' })).toBeInTheDocument();
  });

  it('shows "User not found" for unknown and disabled users', async () => {
    renderRoute('/users/no-such-user');
    expect(await screen.findByText('User not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to problems' })).toBeInTheDocument();
  });

  it('hides disabled users from everyone but admins', async () => {
    users.find((u) => u.username === 'bob')!.enabled = false;
    renderRoute('/users/bob');
    expect(await screen.findByText('User not found')).toBeInTheDocument();
  });
});
