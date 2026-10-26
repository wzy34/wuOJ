import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

describe('routes', () => {
  it('redirects / to /problems', async () => {
    await signIn();
    const { router } = renderRoute('/');
    expect(await screen.findByRole('heading', { name: 'Problems' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/problems');
  });

  it('shows a not-found page with a link to the problem list', async () => {
    await signIn();
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
});
