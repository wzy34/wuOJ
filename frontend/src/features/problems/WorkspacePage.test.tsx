import { act, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse, sse } from 'msw';
import { describe, expect, it } from 'vitest';
import type { SubmissionStatusEvent } from '@/api/generated/model';
import { mockConfig } from '@/mocks/db';
import { server } from '@/mocks/node';
import { apiPost, signIn } from '@/test/api';
import { setViewportWidth } from '@/test/matchMedia';
import { renderRoute } from '@/test/render';
import { STARTER_TEMPLATE } from './starterTemplate';
import { draftKey } from './useDraft';

const editor = () => screen.findByRole('textbox', { name: 'Code editor' });
const resultPanel = () => document.querySelector<HTMLElement>('[aria-label="Submission result"]');

/** Replaces the editor content (the stub is a textarea) in one change event. */
async function setCode(code: string) {
  fireEvent.change(await editor(), { target: { value: code } });
}

function countSubmissionPosts() {
  const posts: string[] = [];
  server.events.on('request:start', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === '/api/submissions') {
      posts.push(request.url);
    }
  });
  return posts;
}

describe('workspace layout', () => {
  it('shows the statement with limits, samples and the editor with the template', async () => {
    await signIn();
    renderRoute('/problems/a-plus-b');

    expect(await screen.findByRole('heading', { name: '1001. A + B' })).toBeInTheDocument();
    expect(screen.getByText('Time limit: 1 s')).toBeInTheDocument();
    expect(screen.getByText('Memory limit: 256 MB')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Samples' })).toBeInTheDocument();
    expect(await editor()).toHaveValue(STARTER_TEMPLATE);
    expect(document.querySelector('.arco-resizebox-split')).not.toBeNull();
    await waitFor(() => expect(document.title).toBe('A + B · wuOJ'));
  });

  it('stacks the statement above the editor on narrow screens', async () => {
    setViewportWidth(700);
    await signIn();
    renderRoute('/problems/a-plus-b');

    await editor();
    expect(document.querySelector('.arco-resizebox-split')).toBeNull();
    const statement = screen.getByRole('heading', { name: '1001. A + B' });
    expect(
      statement.compareDocumentPosition(await editor()) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('shows "Problem not found" for an unknown slug', async () => {
    await signIn();
    renderRoute('/problems/no-such-problem');

    expect(await screen.findByText('Problem not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to problems' })).toHaveAttribute(
      'href',
      '/problems',
    );
  });

  it('resets the code to the template after confirmation', async () => {
    await signIn();
    localStorage.setItem(draftKey(1, 1), 'my old code');
    renderRoute('/problems/a-plus-b');
    expect(await editor()).toHaveValue('my old code');
    const user = userEvent.setup({ pointerEventsCheck: 0 });

    await user.click(screen.getByRole('button', { name: 'Reset to template' }));
    await user.click(await screen.findByRole('button', { name: 'Reset' }));

    expect(await editor()).toHaveValue(STARTER_TEMPLATE);
    expect(localStorage.getItem(draftKey(1, 1))).toBeNull();
  });
});

describe('submitting', () => {
  it('shows the new submission as Pending right away', async () => {
    mockConfig.judgeStepMs = 300;
    await signIn();
    renderRoute('/problems/a-plus-b');
    await setCode('int main() {}');

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    const panel = await waitFor(() => {
      const element = resultPanel();
      expect(element).toHaveTextContent('Submission #7');
      return element!;
    });
    expect(within(panel).getByText('Pending')).toBeInTheDocument();
  });

  it('submits with Ctrl+Enter from the editor', async () => {
    await signIn();
    renderRoute('/problems/a-plus-b');
    await setCode('int main() {} // @mock-verdict: WA');

    fireEvent.keyDown(await editor(), { key: 'Enter', ctrlKey: true });

    expect(await screen.findByText('Wrong Answer')).toBeInTheDocument();
  });

  it('creates one submission on a double click', async () => {
    const posts = countSubmissionPosts();
    await signIn();
    renderRoute('/problems/a-plus-b');
    await setCode('int main() {}');

    await userEvent.dblClick(screen.getByRole('button', { name: 'Submit' }));

    await waitFor(() => expect(resultPanel()).toHaveTextContent('Submission #7'));
    expect(posts).toHaveLength(1);
    server.events.removeAllListeners();
  });

  it('disables Submit for blank code', async () => {
    await signIn();
    renderRoute('/problems/a-plus-b');
    await setCode('   \n  ');
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();
  });

  it('rejects code over 64 KB before sending', async () => {
    const posts = countSubmissionPosts();
    await signIn();
    renderRoute('/problems/a-plus-b');
    await setCode(`int main() {}\n${'/'.repeat(65_536)}`);

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(
      await screen.findByText('Your code is larger than the 64 KB limit.'),
    ).toBeInTheDocument();
    expect(posts).toHaveLength(0);
    server.events.removeAllListeners();
  });

  it('tells the student how long to wait when rate limited', async () => {
    server.use(
      http.post('/api/submissions', () =>
        HttpResponse.json(
          { status: 429, title: 'Too Many Requests', detail: 'Slow down.' },
          { status: 429, headers: { 'Retry-After': '20' } },
        ),
      ),
    );
    await signIn();
    renderRoute('/problems/a-plus-b');
    await setCode('int main() {}');

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(
      await screen.findByText('Too many submissions. Try again in 20 seconds.'),
    ).toBeInTheDocument();
  });
});

describe('live verdict', () => {
  it('shows Accepted with time and memory, and marks the problem Solved', async () => {
    await signIn();
    renderRoute('/problems/prime-sieve');
    await screen.findByRole('heading', { name: '1003. Prime Sieve' });
    expect(screen.queryByText('Solved')).not.toBeInTheDocument();
    await setCode('int main() {}');

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(await screen.findByText('Accepted')).toBeInTheDocument();
    expect(resultPanel()).toHaveTextContent(/\d+ ms · [\d.]+ (KB|MB)/);
    expect(screen.getByRole('link', { name: 'View details' })).toHaveAttribute(
      'href',
      '/submissions/7',
    );
    expect(await screen.findByText('Solved')).toBeInTheDocument();
  });

  it('shows the compiler output for a compilation error', async () => {
    await signIn();
    renderRoute('/problems/a-plus-b');
    await setCode('int helper() {}');

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(await screen.findByText('Compilation Error')).toBeInTheDocument();
    expect(await screen.findByLabelText('Compiler output')).toHaveTextContent(
      "undefined reference to `main'",
    );
  });

  it('keeps updating by polling when the live connection drops', async () => {
    mockConfig.judgeStepMs = 100;
    server.use(
      sse<{ status: SubmissionStatusEvent }>(
        '/api/submissions/:id/events',
        ({ params, client }) => {
          // The server ends the stream mid-run: the browser reports an error and would reconnect.
          client.send({
            event: 'status',
            data: { submissionId: Number(params.id), status: 'PENDING' },
          });
          client.close();
        },
      ),
    );
    await signIn();
    renderRoute('/problems/a-plus-b');
    await setCode('int main() {} // @mock-verdict: TLE');

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(
      await screen.findByText('Time Limit Exceeded', {}, { timeout: 8000 }),
    ).toBeInTheDocument();
  }, 10_000);
});

describe('live verdict without a stream', () => {
  it('polls when the live connection cannot be opened', async () => {
    server.use(
      http.get('/api/submissions/:id/events', () => new HttpResponse(null, { status: 500 })),
    );
    await signIn();
    renderRoute('/problems/a-plus-b');
    await setCode('int main() {} // @mock-verdict: MLE');

    await userEvent.click(screen.getByRole('button', { name: 'Submit' }));

    expect(
      await screen.findByText('Memory Limit Exceeded', {}, { timeout: 6000 }),
    ).toBeInTheDocument();
  }, 8_000);
});

describe('my submissions tab', () => {
  it("lists the student's submissions for this problem, newest first", async () => {
    await signIn();
    await apiPost('/api/submissions', {
      problemId: 1,
      language: 'CPP17',
      sourceCode: 'int main() {}',
    });
    renderRoute('/problems/a-plus-b');
    await screen.findByRole('heading', { name: '1001. A + B' });

    await act(() => userEvent.click(screen.getByRole('tab', { name: 'My submissions' })));

    const list = await screen.findByRole('region', { name: 'My submissions' });
    const links = within(list).getAllByRole('link');
    expect(links.map((link) => link.textContent)).toEqual(['#7', '#2', '#1']);
    expect(links[0]).toHaveAttribute('href', '/submissions/7');
  });

  it('lets visitors write code but asks them to sign in to submit', async () => {
    const posts = countSubmissionPosts();
    renderRoute('/problems/a-plus-b');
    await setCode('int main() {}');

    expect(await screen.findByRole('button', { name: 'Sign in to submit' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'My submissions' })).not.toBeInTheDocument();
    fireEvent.keyDown(await editor(), { key: 'Enter', ctrlKey: true });
    expect(posts).toHaveLength(0);
    server.events.removeAllListeners();
  });

  it('brings the visitor back to the problem with their code after signing in', async () => {
    const { router } = renderRoute('/problems/a-plus-b');
    await setCode('int main() { /* as a visitor */ }');
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in to submit' }));
    expect(router.state.location.search).toBe('?redirect=%2Fproblems%2Fa-plus-b');

    await userEvent.type(await screen.findByLabelText('Username'), 'alice');
    await userEvent.type(screen.getByLabelText('Password'), 'alice');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('button', { name: 'Submit' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/problems/a-plus-b');
    expect(((await editor()) as HTMLTextAreaElement).value).toBe(
      'int main() { /* as a visitor */ }',
    );
  });

  it("keeps the user's own draft over a guest draft", async () => {
    localStorage.setItem(draftKey('guest', 1), 'guest code');
    localStorage.setItem(draftKey(2, 1), "alice's code");
    renderRoute('/login?redirect=%2Fproblems%2Fa-plus-b');
    await userEvent.type(await screen.findByLabelText('Username'), 'alice');
    await userEvent.type(screen.getByLabelText('Password'), 'alice');
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('button', { name: 'Submit' })).toBeInTheDocument();
    expect(((await editor()) as HTMLTextAreaElement).value).toBe("alice's code");
  });
});
