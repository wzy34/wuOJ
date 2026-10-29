import { cleanup, fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Modal } from '@arco-design/web-react';
import { afterEach, describe, expect, it } from 'vitest';
import { problems } from '@/mocks/db';
import { signIn } from '@/test/api';
import { renderRoute } from '@/test/render';

// Confirmation dialogs live outside the React tree, so they outlive a test unless removed; their
// closing animation never finishes in jsdom.
afterEach(() => {
  // Unmount first, so only the static confirmation dialogs remain to be removed.
  cleanup();
  Modal.destroyAll();
  document.querySelectorAll('body > *').forEach((node) => {
    if (node.querySelector('.arco-modal')) node.remove();
  });
});

/** Arco's popups animate in; jsdom never finishes the animation. */
const user = () => userEvent.setup({ pointerEventsCheck: 0 });

const aPlusB = () => problems.find((p) => p.slug === 'a-plus-b')!;
const text = (bytes: Uint8Array) => new TextDecoder().decode(bytes);

async function openEditor(path = '/admin/problems/1') {
  await signIn('admin');
  const view = renderRoute(path);
  await screen.findByRole('heading', { name: '1001. A + B' });
  return view;
}

async function openTab(name: string) {
  await user().click(screen.getByRole('tab', { name: new RegExp(`^${name}`) }));
}

/** The confirmation dialog with this title. */
async function dialog(title: string) {
  const heading = await screen.findByText(title);
  return within(heading.closest('.arco-modal') as HTMLElement);
}

function rows() {
  return within(screen.getByRole('region', { name: 'Test list' }))
    .getAllByRole('row')
    .slice(1);
}

describe('content editing', () => {
  it('previews the statement as it is typed', async () => {
    await openEditor();
    await openTab('Statement');
    fireEvent.change(screen.getByRole('textbox', { name: 'Statement editor' }), {
      target: { value: 'Compute $a + b$ quickly.' },
    });
    const preview = screen.getByRole('region', { name: 'Statement preview' });
    await waitFor(() => expect(preview.querySelector('.katex')).not.toBeNull());
    expect(aPlusB().statement).not.toContain('quickly');
  });

  it('reorders samples and saves them with the other changes', async () => {
    await openEditor();
    await openTab('Samples');
    await user().click(screen.getByRole('button', { name: 'Move sample 2 up' }));
    expect(screen.getByRole('tab', { name: 'Samples •' })).toBeInTheDocument();
    await user().click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Saved.')).toBeInTheDocument();
    expect(aPlusB().samples.map((s) => s.input)).toEqual(['-5 12\n', '1 2\n']);
    expect(aPlusB().revision).toBe(2);
    expect(screen.getByRole('tab', { name: 'Samples' })).toBeInTheDocument();
  });

  it('asks before changing the slug or hiding a visible problem', async () => {
    await openEditor();
    const u = user();
    const slug = screen.getByRole('textbox', { name: 'Slug' });
    await u.clear(slug);
    await u.type(slug, 'a-plus-b-v2');
    await u.click(screen.getByRole('switch', { name: 'Visible' }));
    await u.click(screen.getByRole('button', { name: 'Save' }));

    const confirm = await dialog('Save these changes?');
    expect(
      confirm.getByText(/Links to \/problems\/a-plus-b will stop working/),
    ).toBeInTheDocument();
    expect(confirm.getByText(/no longer be able to open this problem/)).toBeInTheDocument();
    await u.click(confirm.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(problems[0].slug).toBe('a-plus-b-v2'));
    expect(problems[0].visible).toBe(false);
    expect(
      await screen.findByText(/This problem is hidden: only admins can see it/),
    ).toBeInTheDocument();
  });

  it('shows server field errors next to their fields', async () => {
    await openEditor();
    const u = user();
    const id = screen.getByRole('textbox', { name: 'Display ID' });
    await u.clear(id);
    await u.type(id, '1003');
    await u.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText("is already used by problem 'prime-sieve'")).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Display ID' })).toHaveValue('1003');
  });

  it('keeps the edits on a conflict and offers to reload', async () => {
    await openEditor();
    aPlusB().title = 'Changed elsewhere';
    aPlusB().revision = 7;
    const u = user();
    const title = screen.getByRole('textbox', { name: 'Title' });
    await u.clear(title);
    await u.type(title, 'My title');
    await u.click(screen.getByRole('button', { name: 'Save' }));

    let conflict = await dialog('Someone else changed this problem');
    await u.click(conflict.getByRole('button', { name: 'Keep editing' }));
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('My title');

    await u.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() =>
      expect(screen.getAllByText('Someone else changed this problem')).toHaveLength(1),
    );
    conflict = await dialog('Someone else changed this problem');
    await u.click(conflict.getByRole('button', { name: 'Reload latest' }));
    await waitFor(() =>
      expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Changed elsewhere'),
    );
  });
});

describe('test editing', () => {
  it('lists the saved tests and shows one', async () => {
    await openEditor();
    await openTab('Tests');
    expect(rows()).toHaveLength(5);
    expect(within(rows()[1]).getByText('11 B')).toBeInTheDocument();

    await user().click(screen.getByRole('button', { name: 'View test 2' }));
    const output = await screen.findByRole('region', { name: 'Test 2 output' });
    await waitFor(() => expect(output).toHaveTextContent('a-plus-b 2'));
    expect(within(output).getByRole('link', { name: 'Download' })).toHaveAttribute(
      'href',
      '/api/admin/problems/1/tests/2/output',
    );
  });

  it('pairs dropped files into new tests', async () => {
    await openEditor();
    await openTab('Tests');
    const files = ['11.in', '11.out', '12.in', '12.out'].map((name) => new File([name], name));
    fireEvent.drop(screen.getByRole('region', { name: 'Test list' }), { dataTransfer: { files } });

    expect(rows()).toHaveLength(7);
    expect(within(rows()[5]).getByText('11.in (5 B)')).toBeInTheDocument();
    expect(within(rows()[5]).getByText('11.out (6 B)')).toBeInTheDocument();
    expect(within(rows()[6]).getByText('Unsaved')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save tests' })).toBeEnabled();
  });

  it('marks a test missing a file and holds the save until it is given', async () => {
    await openEditor();
    await openTab('Tests');
    await user().upload(screen.getByLabelText('Add test files'), new File(['x'], '13.in'));

    expect(within(rows()[5]).getByText('Missing output')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save tests' })).toBeDisabled();
    await user().upload(
      screen.getByLabelText('Replace output of test 6'),
      new File(['y'], 'y.out'),
    );
    expect(screen.getByRole('button', { name: 'Save tests' })).toBeEnabled();
  });

  it('saves replaced, reordered and deleted tests as a new version', async () => {
    await openEditor();
    await openTab('Tests');
    const u = user();
    const before = aPlusB().testdataVersion;
    await u.upload(
      screen.getByLabelText('Replace output of test 4'),
      new File(['fixed\n'], '4.out'),
    );
    await u.click(screen.getByRole('button', { name: 'Move test 2 up' }));
    await u.click(screen.getByRole('button', { name: 'Delete test 5' }));
    await u.click(screen.getByRole('button', { name: 'Save tests' }));

    expect(await screen.findByText('Saved 4 tests as a new version.')).toBeInTheDocument();
    expect(aPlusB().testdataVersion).not.toBe(before);
    expect(aPlusB().tests.map((t) => text(t.input))).toEqual(['2\n', '1\n', '3\n', '4\n']);
    expect(text(aPlusB().tests[3].output)).toBe('fixed\n');
    await waitFor(() => expect(screen.queryByText('Unsaved')).not.toBeInTheDocument());
  });

  it('edits a small test in the page', async () => {
    await openEditor();
    await openTab('Tests');
    const u = user();
    await u.click(screen.getByRole('button', { name: 'Edit test 1' }));
    const output = await screen.findByRole('textbox', { name: 'Test 1 output' });
    await waitFor(() => expect(output).toHaveValue('a-plus-b 1\n'));
    await u.clear(output);
    await u.type(output, 'edited');
    await u.click(screen.getByRole('button', { name: 'Apply' }));

    expect(within(rows()[0]).getByText('1.out (6 B)')).toBeInTheDocument();
    expect(within(rows()[0]).getByText('Unsaved')).toBeInTheDocument();
  });

  it('asks before leaving with unsaved tests', async () => {
    const { router } = await openEditor();
    await openTab('Tests');
    const u = user();
    await u.click(screen.getByRole('button', { name: 'Delete test 1' }));
    await u.click(screen.getByRole('link', { name: 'View problem' }));

    const confirm = await dialog('Leave without saving?');
    await u.click(confirm.getByRole('button', { name: 'Stay' }));
    expect(router.state.location.pathname).toBe('/admin/problems/1');
    expect(rows()).toHaveLength(4);
  });
});

describe('creating a problem', () => {
  async function fillIn(slug: string) {
    await signIn('admin');
    const view = renderRoute('/admin/problems/new');
    await screen.findByRole('heading', { name: 'New problem' });
    const u = user();
    await u.type(screen.getByRole('textbox', { name: 'Display ID' }), '2001');
    await u.type(screen.getByRole('textbox', { name: 'Slug' }), slug);
    await u.type(screen.getByRole('textbox', { name: 'Title' }), 'Echo');
    await openTab('Statement');
    fireEvent.change(screen.getByRole('textbox', { name: 'Statement editor' }), {
      target: { value: 'Print the input.' },
    });
    await openTab('Tests');
    expect(screen.getByRole('button', { name: 'Create problem' })).toBeDisabled();
    await u.upload(screen.getByLabelText('Add test files'), [
      new File(['1'], '1.in'),
      new File(['1'], '1.out'),
    ]);
    return view;
  }

  it('creates a hidden problem and opens its editor', async () => {
    const { router } = await fillIn('echo');
    await user().click(screen.getByRole('button', { name: 'Create problem' }));

    expect(await screen.findByRole('heading', { name: '2001. Echo' })).toBeInTheDocument();
    const created = problems.find((p) => p.slug === 'echo')!;
    expect(created).toMatchObject({ visible: false, testCount: 1 });
    expect(router.state.location.pathname).toBe(`/admin/problems/${created.id}`);
    expect(screen.getByText(/This problem is hidden/)).toBeInTheDocument();
  });

  it('shows a taken slug under the slug field and keeps what was entered', async () => {
    await fillIn('a-plus-b');
    await user().click(screen.getByRole('button', { name: 'Create problem' }));

    expect(await screen.findByText('is already used by another problem')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Details' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Echo');
  });
});
