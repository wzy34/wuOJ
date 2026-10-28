import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

describe('routes', () => {
  it('redirects / to /problems, signed in or not', async () => {
    const { router } = renderRoute('/');
    expect(await screen.findByRole('heading', { name: 'Problems' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/problems');
  });

  it('shows a not-found page with a link to the problem list', async () => {
    renderRoute('/does-not-exist');
    expect(await screen.findByText('Page not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to problems' })).toHaveAttribute(
      'href',
      '/problems',
    );
  });

  it('renders a deep link directly', async () => {
    await signIn();
    renderRoute('/submissions/2');
    expect(await screen.findByRole('heading', { name: 'Submission #2' })).toBeInTheDocument();
  });

  it('loads the problem workspace lazily', async () => {
    await signIn();
    renderRoute('/problems/a-plus-b');
    await waitFor(() => expect(screen.getByText(/A \+ B|a-plus-b/)).toBeInTheDocument());
  });

  it('serves profiles to visitors', async () => {
    renderRoute('/users/alice');
    expect(await screen.findByRole('heading', { name: 'alice' })).toBeInTheDocument();
  });

  it('shows "page not found" for admin pages to users who are not admins', async () => {
    await signIn('alice');
    renderRoute('/admin/users');
    expect(await screen.findByText('Page not found')).toBeInTheDocument();
  });

  it('sends visitors to sign in for admin pages', async () => {
    const { router } = renderRoute('/admin/users');
    await screen.findByLabelText('Username');
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.search).toBe('?redirect=%2Fadmin%2Fusers');
  });

  it('opens the admin pages for admins', async () => {
    await signIn('admin');
    const { router } = renderRoute('/admin');
    expect(await screen.findByRole('tab', { name: 'Accounts' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/admin/users');
  });
});
