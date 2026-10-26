import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { mockConfig } from '@/mocks/db';
import { apiPost, signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

function idLinks() {
  return screen
    .getAllByRole('link')
    .map((link) => link.textContent ?? '')
    .filter((text) => /^#\d+$/.test(text));
}

describe('SubmissionListPage', () => {
  it("shows the student's own submissions by default, newest first", async () => {
    await signIn();
    renderRoute('/submissions');

    await screen.findByRole('link', { name: '#5' });
    expect(idLinks()).toEqual(['#5', '#2', '#1']);
    const row = within(screen.getByRole('link', { name: '#5' }).closest('tr')!);
    expect(row.getByText('student')).toBeInTheDocument();
    expect(row.getByRole('link', { name: '1002. Maximum Subarray Sum' })).toBeInTheDocument();
    expect(row.getByText('C++17')).toBeInTheDocument();
    expect(row.getByText('Time Limit Exceeded')).toBeInTheDocument();
    expect(row.getByText('1000 ms')).toBeInTheDocument();
    expect(row.getByText('5.0 MB')).toBeInTheDocument();
  });

  it('switches to all users', async () => {
    await signIn();
    const { router } = renderRoute('/submissions');
    await screen.findByRole('link', { name: '#5' });

    await userEvent.click(screen.getByText('All users'));

    await waitFor(() => expect(idLinks()).toEqual(['#6', '#5', '#4', '#3', '#2', '#1']));
    expect(router.state.location.search).toBe('?scope=all');
  });

  it('filters by verdict from page 1 and puts the filter in the URL', async () => {
    await signIn();
    const { router } = renderRoute('/submissions?scope=all');
    await screen.findByRole('link', { name: '#6' });
    const user = userEvent.setup({ pointerEventsCheck: 0 });

    await user.click(screen.getByRole('combobox', { name: 'Filter by verdict' }));
    await user.click(await screen.findByRole('option', { name: 'Accepted' }));

    await waitFor(() => expect(idLinks()).toEqual(['#3', '#2']));
    expect(router.state.location.search).toBe('?scope=all&verdict=AC');
  });

  it('opens a shared link with its filters', async () => {
    await signIn();
    renderRoute('/submissions?scope=all&problem=prime-sieve');

    await screen.findByRole('link', { name: '#4' });
    expect(idLinks()).toEqual(['#4']);
    expect(screen.getByText('Compilation Error')).toBeInTheDocument();
    const problemFilter = screen.getByRole('combobox', { name: 'Filter by problem' });
    expect(await within(problemFilter).findByText('1003. Prime Sieve')).toBeInTheDocument();
  });

  it('refreshes a running row until it has a verdict', async () => {
    mockConfig.judgeStepMs = 150;
    await signIn();
    await apiPost('/api/submissions', {
      problemId: 1,
      language: 'CPP17',
      sourceCode: 'int main() {}',
    });
    renderRoute('/submissions');

    const row = await screen.findByRole('link', { name: '#7' });
    expect(within(row.closest('tr')!).queryByText('Accepted')).not.toBeInTheDocument();

    await waitFor(
      () =>
        expect(
          within(screen.getByRole('link', { name: '#7' }).closest('tr')!).getByText('Accepted'),
        ).toBeInTheDocument(),
      { timeout: 5000 },
    );
  }, 8_000);
});
