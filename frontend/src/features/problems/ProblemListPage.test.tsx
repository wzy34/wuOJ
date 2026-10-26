import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

function rows() {
  return screen.getAllByRole('row').slice(1);
}

describe('ProblemListPage', () => {
  it('lists the first page with number, title, difficulty and status', async () => {
    await signIn();
    renderRoute('/problems');

    const first = await screen.findByRole('link', { name: 'A + B' });
    expect(rows()).toHaveLength(20);
    const row = within(first.closest('tr')!);
    expect(row.getByText('1001')).toBeInTheDocument();
    expect(row.getByText('Easy')).toBeInTheDocument();
    expect(row.getByRole('img', { name: 'Solved' })).toBeInTheDocument();

    const attempted = within(
      screen.getByRole('link', { name: 'Maximum Subarray Sum' }).closest('tr')!,
    );
    expect(attempted.getByRole('img', { name: 'Attempted' })).toBeInTheDocument();
    expect(screen.getByText('Total: 45')).toBeInTheDocument();
  });

  it('opens a problem from its title', async () => {
    await signIn();
    const { router } = renderRoute('/problems');
    await userEvent.click(await screen.findByRole('link', { name: 'A + B' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems/a-plus-b'));
  });

  it('searches by title once typing pauses and resets to page 1', async () => {
    await signIn();
    const { router } = renderRoute('/problems?page=2');
    await screen.findByRole('link', { name: /Warm-up 15/ });

    await userEvent.type(
      screen.getByRole('textbox', { name: 'Search problems by title' }),
      'graph',
    );

    expect(await screen.findByRole('link', { name: 'Bipartite Graph Check' })).toBeInTheDocument();
    await waitFor(() => expect(rows()).toHaveLength(1));
    expect(router.state.location.search).toBe('?q=graph');
  });

  it('filters by difficulty from the dropdown', async () => {
    await signIn();
    const { router } = renderRoute('/problems');
    await screen.findByRole('link', { name: 'A + B' });
    // Arco's dropdown animates in with pointer-events: none, and jsdom never finishes the animation.
    const user = userEvent.setup({ pointerEventsCheck: 0 });

    await user.click(screen.getByRole('combobox', { name: 'Filter by difficulty' }));
    await user.click(await screen.findByRole('option', { name: 'Hard' }));

    await waitFor(() =>
      expect(screen.queryByRole('link', { name: 'A + B' })).not.toBeInTheDocument(),
    );
    expect(screen.getByRole('link', { name: 'Counting Inversions' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?difficulty=HARD');
  });

  it('shows an empty state that clears the search', async () => {
    await signIn();
    const { router } = renderRoute('/problems?q=zzz');

    expect(await screen.findByText('No problems match your search')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }));

    expect(await screen.findByRole('link', { name: 'A + B' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('');
    expect(screen.getByRole('textbox', { name: 'Search problems by title' })).toHaveValue('');
  });

  it('restores page and search with the back button', async () => {
    await signIn();
    const { router } = renderRoute('/problems?q=warm&page=2');
    const link = await screen.findByRole('link', { name: /Warm-up 37/ });

    await userEvent.click(link);
    await waitFor(() => expect(router.state.location.pathname).toBe('/problems/warm-up-37'));

    await act(() => router.navigate(-1));
    expect(await screen.findByRole('link', { name: /Warm-up 37/ })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?q=warm&page=2');
    expect(screen.getByRole('textbox', { name: 'Search problems by title' })).toHaveValue('warm');
  });
});
