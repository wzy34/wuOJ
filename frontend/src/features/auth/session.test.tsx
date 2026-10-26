import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { customInstance } from '@/api/http';
import { SESSION_EXPIRED_MESSAGE } from '@/app/RootLayout';
import { setSessionUser } from '@/mocks/db';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

async function signInThroughForm(username: string, password = username) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText('Username'), username);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('protected pages', () => {
  it('sends a signed-out visitor to login and back to the requested page', async () => {
    const { router } = renderRoute('/problems/a-plus-b');

    await screen.findByRole('button', { name: 'Sign in' });
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.search).toBe('?redirect=%2Fproblems%2Fa-plus-b');

    await signInThroughForm('student');
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems/a-plus-b'));
  });

  it('restores the session on reload without showing the login page', async () => {
    await signIn();
    const { router } = renderRoute('/submissions');
    const visited: string[] = [];
    const unsubscribe = router.subscribe((state) => visited.push(state.location.pathname));

    expect(await screen.findByRole('heading', { name: 'Submissions' })).toBeInTheDocument();
    expect(visited).not.toContain('/login');
    expect(screen.queryByRole('button', { name: 'Sign in' })).not.toBeInTheDocument();
    unsubscribe();
  });

  it('redirects a signed-in student away from the login page', async () => {
    await signIn();
    const { router } = renderRoute('/login');
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems'));
  });
});

describe('session expiry', () => {
  it('returns to login with a message and comes back afterwards', async () => {
    await signIn();
    const { router } = renderRoute('/problems');
    await screen.findByRole('heading', { name: 'Problems' });

    setSessionUser(null);
    await act(async () => {
      await customInstance({ url: '/api/problems', method: 'GET' }).catch(() => {});
    });

    expect(await screen.findByText(SESSION_EXPIRED_MESSAGE)).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(router.state.location.search).toBe('');

    await signInThroughForm('student');
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems'));
  });
});

describe('sign out', () => {
  it('ends the session and leaves no data for the next student', async () => {
    await signIn();
    const { router, queryClient } = renderRoute('/problems');
    // Arco's dropdown animates in with pointer-events: none, and jsdom never finishes the animation.
    const user = userEvent.setup({ pointerEventsCheck: 0 });

    await user.click(await screen.findByRole('button', { name: 'User menu' }));
    await user.click(await screen.findByText('Sign out'));

    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    await screen.findByRole('button', { name: 'Sign in' });
    const cached = queryClient.getQueryCache().getAll();
    expect(cached.every((query) => query.state.data === undefined)).toBe(true);

    await signInThroughForm('alice');
    expect(await screen.findByRole('button', { name: 'User menu' })).toHaveTextContent('alice');
  });
});
