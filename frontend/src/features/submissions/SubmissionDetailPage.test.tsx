import { screen, waitFor, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { setSessionUser } from '@/mocks/db';
import { server } from '@/mocks/node';
import { apiPost, signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

/** Rows of the "Test results" card, without its header row. */
function testRows() {
  const card = screen.getByText('Test results').closest<HTMLElement>('.arco-card')!;
  return within(card).getAllByRole('row').slice(1);
}

describe('SubmissionDetailPage', () => {
  it('shows the owner the source and per-test results of a TLE', async () => {
    await signIn();
    renderRoute('/submissions/5');

    expect(await screen.findByRole('heading', { name: 'Submission #5' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '1002. Maximum Subarray Sum' })).toHaveAttribute(
      'href',
      '/problems/maximum-subarray-sum',
    );
    const source = await screen.findByRole('textbox', { name: 'Source code' });
    expect((source as HTMLTextAreaElement).value).toContain('O(n^2) brute force');
    expect(screen.getByRole('button', { name: 'Copy source code' })).toBeInTheDocument();

    const tests = testRows();
    expect(tests).toHaveLength(3);
    expect(within(tests[2]).getByText('Time Limit Exceeded')).toBeInTheDocument();
    expect(within(tests[0]).getByText('Accepted')).toBeInTheDocument();
  });

  it("hides another student's source code but shows the verdict", async () => {
    await signIn();
    renderRoute('/submissions/6');

    expect(await screen.findByRole('heading', { name: 'Submission #6' })).toBeInTheDocument();
    expect(screen.getByText('Source code is only visible to its author.')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: 'Source code' })).not.toBeInTheDocument();
    expect(screen.getAllByText('Wrong Answer').length).toBeGreaterThan(0);
  });

  it('shows the compiler output for the owner of a compilation error', async () => {
    await signIn('bob');
    renderRoute('/submissions/4');

    expect(await screen.findByLabelText('Compiler output')).toHaveTextContent(
      "'cot' was not declared",
    );
  });

  it('updates live while the submission is judged', async () => {
    await signIn();
    const created = await (
      await apiPost('/api/submissions', {
        problemId: 1,
        language: 'CPP17',
        sourceCode: 'int main() {} // @mock-verdict: RE',
      })
    ).json();
    renderRoute(`/submissions/${created.id}`);

    expect(await screen.findByText('Runtime Error')).toBeInTheDocument();
    // The final event carries the verdict; the per-test rows arrive with the refetch it triggers.
    await waitFor(() => expect(testRows()).toHaveLength(3));
  });

  it('shows "Submission not found" for an unknown id', async () => {
    await signIn();
    renderRoute('/submissions/999999');

    expect(await screen.findByText('Submission not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to submissions' })).toHaveAttribute(
      'href',
      '/submissions',
    );
  });

  it('shows admins the source code of any submission', async () => {
    await signIn('admin');
    renderRoute('/submissions/6');
    const source = await screen.findByRole('textbox', { name: 'Source code' });
    expect((source as HTMLTextAreaElement).value).toContain('int main');
    expect(screen.getByRole('button', { name: 'Copy source code' })).toBeInTheDocument();
  });

  it('shows visitors the verdict without the source code, linking the user', async () => {
    renderRoute('/submissions/6');
    expect(await screen.findByRole('heading', { name: 'Submission #6' })).toBeInTheDocument();
    expect(screen.getByText('Source code is only visible to its author.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'alice' })).toHaveAttribute('href', '/users/alice');
  });

  it('checks a submission by polling for visitors, without the live stream', async () => {
    const streams: string[] = [];
    server.events.on('request:start', ({ request }) => {
      if (request.url.endsWith('/events')) streams.push(request.url);
    });
    await signIn();
    const created = await (
      await apiPost('/api/submissions', {
        problemId: 1,
        language: 'CPP17',
        sourceCode: 'int main() {} // @mock-verdict: WA',
      })
    ).json();
    setSessionUser(null);
    renderRoute(`/submissions/${created.id}`);

    expect(
      (await screen.findAllByText('Wrong Answer', {}, { timeout: 4000 })).length,
    ).toBeGreaterThan(0);
    expect(streams).toHaveLength(0);
    server.events.removeAllListeners();
  });
});
