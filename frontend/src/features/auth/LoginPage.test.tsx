import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { server } from '@/mocks/node';
import { renderRoute } from '@/test/render';
import { safeRedirect } from './safeRedirect';

async function fillAndSubmit(username: string, password: string) {
  const user = userEvent.setup();
  const usernameField = await screen.findByLabelText('Username');
  if (username) await user.type(usernameField, username);
  if (password) await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginPage', () => {
  it('signs in and opens the problem list', async () => {
    const { router } = renderRoute('/login');
    await fillAndSubmit('student', 'student');

    expect(await screen.findByRole('button', { name: 'User menu' })).toHaveTextContent('student');
    expect(router.state.location.pathname).toBe('/problems');
  });

  it('keeps the username and clears the password after a wrong password', async () => {
    renderRoute('/login');
    await fillAndSubmit('student', 'wrong');

    expect(await screen.findByText('Invalid username or password')).toBeInTheDocument();
    expect(screen.getByLabelText('Username')).toHaveValue('student');
    await waitFor(() => expect(screen.getByLabelText('Password')).toHaveValue(''));
  });

  it('asks for missing fields without sending a request', async () => {
    const logins: string[] = [];
    server.events.on('request:start', ({ request }) => {
      if (request.url.endsWith('/api/auth/login')) logins.push(request.url);
    });
    renderRoute('/login');
    await fillAndSubmit('', 'student');

    expect(await screen.findByText('Enter your username')).toBeInTheDocument();
    expect(logins).toHaveLength(0);
    server.events.removeAllListeners();
  });

  it('ignores redirects to other sites', async () => {
    const { router } = renderRoute('/login?redirect=https://evil.example');
    await fillAndSubmit('student', 'student');

    await waitFor(() => expect(router.state.location.pathname).toBe('/problems'));
  });
});

describe('safeRedirect', () => {
  it.each([
    ['/problems/a-plus-b', '/problems/a-plus-b'],
    ['/submissions?scope=all', '/submissions?scope=all'],
    [null, '/problems'],
    ['', '/problems'],
    ['https://evil.example', '/problems'],
    ['//evil.example', '/problems'],
    ['/\\evil.example', '/problems'],
    ['javascript:alert(1)', '/problems'],
  ])('maps %s to %s', (input, expected) => {
    expect(safeRedirect(input)).toBe(expected);
  });
});
