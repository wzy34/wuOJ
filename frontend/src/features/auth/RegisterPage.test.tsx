import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { mockConfig } from '@/mocks/db';
import { server } from '@/mocks/node';
import { renderRoute } from '@/test/render';

async function fill(username: string, password: string, confirm = password) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText('Username'), username);
  await user.type(screen.getByLabelText('Password'), password);
  await user.type(screen.getByLabelText('Confirm password'), confirm);
  await user.click(screen.getByRole('button', { name: 'Create account' }));
}

describe('RegisterPage', () => {
  it('registers, signs in and returns to the page the visitor came from', async () => {
    const { router } = renderRoute('/register?redirect=%2Fproblems%2Fa-plus-b');
    await fill('carol', 'correct horse battery');
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems/a-plus-b'));
    expect(await screen.findByRole('button', { name: 'User menu' })).toHaveTextContent('carol');
  });

  it('shows a taken username next to the field and keeps the passwords', async () => {
    renderRoute('/register');
    await fill('alice', 'correct horse battery');
    expect(await screen.findByText('is already taken')).toBeInTheDocument();
    expect(screen.getByLabelText('Password')).toHaveValue('correct horse battery');
    expect(screen.getByLabelText('Confirm password')).toHaveValue('correct horse battery');
  });

  it('shows a refused password next to the field', async () => {
    renderRoute('/register');
    await fill('carol', 'password123');
    expect(await screen.findByText(/too commonly used/)).toBeInTheDocument();
  });

  it('checks the fields before sending', async () => {
    const posts: string[] = [];
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'POST') posts.push(request.url);
    });
    renderRoute('/register');
    await fill('bad name', 'short', 'different');
    expect(
      await screen.findByText("Use only letters, digits, '.', '_' and '-'"),
    ).toBeInTheDocument();
    expect(screen.getByText('Use at least 8 characters')).toBeInTheDocument();
    expect(screen.getByText('The passwords do not match')).toBeInTheDocument();
    expect(posts).toHaveLength(0);
    server.events.removeAllListeners();
  });

  it('says so when registration is closed', async () => {
    mockConfig.registrationEnabled = false;
    renderRoute('/register');
    expect(await screen.findByText('Registration is closed.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
    expect(screen.queryByLabelText('Username')).not.toBeInTheDocument();
  });

  it('is linked from the login page while registration is open', async () => {
    renderRoute('/login?redirect=%2Fsubmissions');
    expect(await screen.findByRole('link', { name: 'Create an account' })).toHaveAttribute(
      'href',
      '/register?redirect=%2Fsubmissions',
    );
  });
});
