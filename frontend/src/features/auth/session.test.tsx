import { focusManager } from '@tanstack/react-query';
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
  it('shows public pages to visitors without a detour to login', async () => {
    const { router } = renderRoute('/problems/a-plus-b');
    // The workspace chunk (editor, math) loads lazily; the first load in a test run is slow.
    expect(
      await screen.findByRole('button', { name: 'Sign in to submit' }, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/problems/a-plus-b');
  });

  it('sends a visitor to login for settings and back afterwards', async () => {
    const { router } = renderRoute('/settings');

    await screen.findByLabelText('Username');
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.search).toBe('?redirect=%2Fsettings');

    await signInThroughForm('student');
    expect(await screen.findByRole('heading', { name: 'Settings' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/settings');
  });

  it('ignores redirects to other sites', async () => {
    const { router } = renderRoute('/login?redirect=https://evil.example');
    await signInThroughForm('student');
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems'));
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

  it('redirects a signed-in user away from the login and register pages', async () => {
    await signIn();
    const { router } = renderRoute('/login');
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems'));
    await act(() => router.navigate('/register'));
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems'));
  });
});

describe('session expiry', () => {
  it('returns to login with a message when an action needs the session', async () => {
    await signIn();
    const { router } = renderRoute('/problems');
    await screen.findByRole('heading', { name: 'Problems' });

    setSessionUser(null);
    await act(async () => {
      await customInstance({ url: '/api/submissions', method: 'GET' }).catch(() => {});
    });

    expect(await screen.findByText(SESSION_EXPIRED_MESSAGE)).toBeInTheDocument();
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(router.state.location.search).toBe('');

    await signInThroughForm('student');
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems'));
  });

  it('turns the page into the visitor view when the session ended elsewhere', async () => {
    await signIn();
    const { router } = renderRoute('/problems');
    expect(await screen.findByRole('columnheader', { name: 'Status' })).toBeInTheDocument();

    setSessionUser(null);
    act(() => {
      focusManager.setFocused(false);
      focusManager.setFocused(true);
    });

    expect(await screen.findByText(SESSION_EXPIRED_MESSAGE)).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Sign in' })).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('columnheader', { name: 'Status' })).not.toBeInTheDocument(),
    );
    expect(router.state.location.pathname).toBe('/problems');
    act(() => focusManager.setFocused(undefined));
  });
});

describe('sign out', () => {
  it('ends the session and leaves no data for the next user', async () => {
    await signIn();
    const { router, queryClient } = renderRoute('/problems');
    // Arco's dropdown animates in with pointer-events: none, and jsdom never finishes the animation.
    const user = userEvent.setup({ pointerEventsCheck: 0 });

    await user.click(await screen.findByRole('button', { name: 'User menu' }));
    await user.click(await screen.findByText('Sign out'));

    await waitFor(() => expect(router.state.location.pathname).toBe('/problems'));
    await user.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(
      queryClient
        .getQueryCache()
        .getAll()
        .every((query) => query.queryKey[0] !== '/api/submissions'),
    ).toBe(true);
    expect(screen.queryByRole('columnheader', { name: 'Status' })).not.toBeInTheDocument();

    await signInThroughForm('alice');
    expect(await screen.findByRole('button', { name: 'User menu' })).toHaveTextContent('alice');
  });
});
