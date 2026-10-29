import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

const titles = () =>
  screen
    .getAllByRole('row')
    .slice(1)
    .map((row) => within(row).getAllByRole('cell')[1].textContent);

describe('AdminProblemsPage', () => {
  it('lists every problem, hidden ones included, linking to the editor', async () => {
    await signIn('admin');
    renderRoute('/admin/problems?page=3');
    const draft = await screen.findByRole('link', { name: 'Shortest Path (draft)' });
    expect(draft).toHaveAttribute('href', '/admin/problems/46');
    const row = within(draft.closest('tr')!);
    expect(row.getByText('Hidden')).toBeInTheDocument();
    expect(row.getByText('Hard')).toBeInTheDocument();
    expect(row.getByText('3')).toBeInTheDocument();
  });

  it('filters by visibility and keeps the filter in the URL', async () => {
    await signIn('admin');
    const { router } = renderRoute('/admin/problems?page=2');
    await screen.findByRole('link', { name: 'Warm-up 15: Multiply by 16' });

    // The dropdown animates in; jsdom never finishes the animation.
    const user = userEvent.setup({ pointerEventsCheck: 0 });
    await user.click(screen.getByLabelText('Filter by visibility'));
    await user.click(await screen.findByRole('option', { name: 'Hidden' }));

    await waitFor(() => expect(router.state.location.search).toBe('?visible=false'));
    await waitFor(() => expect(titles()).toEqual(['Shortest Path (draft)']));
  });

  it('searches by title and opens the new problem page', async () => {
    await signIn('admin');
    const { router } = renderRoute('/admin/problems');
    await screen.findByRole('link', { name: 'A + B' });
    await userEvent.type(screen.getByLabelText('Search problems by title'), 'sieve');
    await waitFor(() => expect(titles()).toEqual(['Prime Sieve']));
    expect(router.state.location.search).toBe('?q=sieve');

    await userEvent.click(screen.getByRole('button', { name: /New problem/ }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/admin/problems/new'));
  });
});
