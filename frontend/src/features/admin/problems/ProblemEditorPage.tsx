import { Alert, Button, Form, Message, Modal, Tabs, Typography } from '@arco-design/web-react';
import { useQueryClient, type QueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import {
  getGetAdminProblemQueryKey,
  getListAdminProblemTestsQueryKey,
  useCreateAdminProblem,
  useGetAdminProblem,
  useListAdminProblemTests,
  useSaveAdminProblemTests,
  useUpdateAdminProblem,
} from '@/api/generated/admin/admin';
import type { AdminProblem, AdminTestSet, ProblemContent } from '@/api/generated/model';
import { getApiProblem, getHttpStatus } from '@/api/http';
import { NotFoundPage } from '@/app/NotFoundPage';
import { PageError, PageLoading } from '@/components/PageState';
import { showFieldErrors } from '@/features/auth/formErrors';
import { usePageTitle } from '@/lib/usePageTitle';
import adminStyles from '../Admin.module.css';
import { AdminTabs } from '../AdminTabs';
import {
  CONTENT_FIELDS,
  FIELD_TABS,
  NEW_PROBLEM,
  sameContent,
  toContent,
  type ContentTab,
} from './content';
import { ContentFields } from './ContentFields';
import styles from './ProblemEditor.module.css';
import { TestsEditor } from './TestsEditor';
import { useUnsavedChangesGuard } from './useUnsavedChangesGuard';
import { buildManifest, useTestDraft } from './useTestDraft';

type Tab = ContentTab | 'tests';

const TAB_TITLES: Record<Tab, string> = {
  details: 'Details',
  statement: 'Statement',
  samples: 'Samples',
  tests: 'Tests',
};

/** Public and admin problem data, and the audit log, all show what was just saved. */
function refreshProblems(queryClient: QueryClient) {
  return queryClient.invalidateQueries({
    predicate: (query) => {
      const key = query.queryKey[0];
      return (
        typeof key === 'string' &&
        (key.startsWith('/api/problems') || key.startsWith('/api/admin/'))
      );
    },
  });
}

function EditorTabs({
  tab,
  onChange,
  unsaved,
}: {
  tab: Tab;
  onChange: (tab: Tab) => void;
  unsaved: Tab[];
}) {
  return (
    <Tabs activeTab={tab} onChange={(key) => onChange(key as Tab)} className={adminStyles.tabs}>
      {(Object.keys(TAB_TITLES) as Tab[]).map((key) => (
        <Tabs.TabPane
          key={key}
          title={unsaved.includes(key) ? `${TAB_TITLES[key]} •` : TAB_TITLES[key]}
        />
      ))}
    </Tabs>
  );
}

/** The tab of the first invalid field, after a failed client-side or server check. */
function firstErrorTab(fields: string[]): Tab | undefined {
  for (const field of fields) {
    const tab = FIELD_TABS[field.split(/[.[]/)[0]];
    if (tab) return tab;
  }
  return undefined;
}

export function ProblemEditorPage() {
  const { id } = useParams();
  if (id === 'new') return <CreateProblem />;
  const problemId = Number(id);
  if (!Number.isInteger(problemId) || problemId < 1) return <NotFoundPage />;
  return <EditProblem key={problemId} problemId={problemId} />;
}

function CreateProblem() {
  usePageTitle('New problem');
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ProblemContent>();
  const [tab, setTab] = useState<Tab>('details');
  const [contentDirty, setContentDirty] = useState(false);
  const draft = useTestDraft(undefined);
  const { allowLeave } = useUnsavedChangesGuard(contentDirty || draft.tests.length > 0);

  const create = useCreateAdminProblem({
    mutation: {
      onSuccess: (created) => {
        Message.success('Problem created. It stays hidden until you publish it.');
        void refreshProblems(queryClient);
        allowLeave();
        void navigate(`/admin/problems/${created.id}`, { replace: true });
      },
      onError: (failure) => {
        if (showFieldErrors(form, failure, CONTENT_FIELDS)) {
          const fields = getApiProblem(failure)?.errors?.map((e) => e.field) ?? [];
          setTab(firstErrorTab(fields) ?? tab);
          return;
        }
        Message.error(
          getApiProblem(failure)?.errors?.[0]?.message ??
            getApiProblem(failure)?.detail ??
            'The problem could not be created.',
        );
      },
    },
  });

  const submit = async () => {
    let values: ProblemContent;
    try {
      values = await form.validate();
    } catch (invalid) {
      setTab(firstErrorTab(Object.keys(invalid ?? {})) ?? tab);
      return;
    }
    const { manifest, files } = buildManifest(draft.tests);
    create.mutate({ data: { problem: { ...toContent(values), visible: false }, manifest, files } });
  };

  return (
    <section>
      <AdminTabs />
      <div className={styles.header}>
        <Typography.Title heading={5}>New problem</Typography.Title>
      </div>
      <Alert
        className={styles.notice}
        content="New problems are hidden. Publish one by turning on Visible in its details after creating it."
      />
      <EditorTabs tab={tab} onChange={setTab} unsaved={[]} />
      <Form
        form={form}
        layout="vertical"
        initialValues={NEW_PROBLEM}
        onValuesChange={(_, values) =>
          setContentDirty(!sameContent(values as ProblemContent, NEW_PROBLEM))
        }
        hidden={tab === 'tests'}
      >
        <ContentFields tab={tab} showVisible={false} />
      </Form>
      <div hidden={tab !== 'tests'}>
        <TestsEditor draft={draft} />
      </div>
      <div className={styles.actions}>
        <Button
          type="primary"
          loading={create.isPending}
          disabled={!draft.complete}
          onClick={() => void submit()}
        >
          Create problem
        </Button>
        {!draft.complete && (
          <span className={styles.hint}>
            Add at least one test with both an input and an output in the Tests tab.
          </span>
        )}
      </div>
    </section>
  );
}

function EditProblem({ problemId }: { problemId: number }) {
  const queryClient = useQueryClient();
  const problem = useGetAdminProblem(problemId);
  const tests = useListAdminProblemTests(problemId);
  usePageTitle(problem.data ? `Edit ${problem.data.content.title}` : 'Edit problem');

  if (problem.isPending || tests.isPending) return <PageLoading />;
  if (getHttpStatus(problem.error) === 404) return <NotFoundPage />;
  if (problem.isError || tests.isError) {
    return (
      <PageError
        onRetry={() => {
          void problem.refetch();
          void tests.refetch();
        }}
      />
    );
  }
  return (
    <Editor
      problemId={problemId}
      loaded={problem.data}
      reload={async () => (await problem.refetch()).data}
      reloadTests={() =>
        queryClient.invalidateQueries({ queryKey: getListAdminProblemTestsQueryKey(problemId) })
      }
      testSet={tests.data}
    />
  );
}

function Editor({
  problemId,
  loaded,
  reload,
  reloadTests,
  testSet,
}: {
  problemId: number;
  loaded: AdminProblem;
  reload: () => Promise<AdminProblem | undefined>;
  reloadTests: () => Promise<void>;
  testSet: AdminTestSet;
}) {
  const queryClient = useQueryClient();
  const [form] = Form.useForm<ProblemContent>();
  const [tab, setTab] = useState<Tab>('details');
  // What the form was loaded from; it changes only on save or an explicit reload, never by refetching.
  const [baseline, setBaseline] = useState(loaded);
  const [formKey, setFormKey] = useState(0);
  const [contentDirty, setContentDirty] = useState(false);
  const draft = useTestDraft(testSet);
  useUnsavedChangesGuard(contentDirty || draft.dirty);

  const resetTo = (problem: AdminProblem) => {
    setBaseline(problem);
    setFormKey((key) => key + 1);
    setContentDirty(false);
    queryClient.setQueryData(getGetAdminProblemQueryKey(problemId), problem);
  };

  const conflict = (what: 'content' | 'tests', detail?: string) =>
    Modal.confirm({
      title:
        what === 'content' ? 'Someone else changed this problem' : 'Someone else saved new tests',
      content: `${detail ?? 'Your save was refused because it was based on an older version.'} Your changes are still on this page.`,
      okText: 'Reload latest',
      cancelText: 'Keep editing',
      onOk: async () => {
        if (what === 'content') {
          const fresh = await reload();
          if (fresh) resetTo(fresh);
        } else {
          await reloadTests();
          draft.discard();
        }
      },
    });

  const update = useUpdateAdminProblem({
    mutation: {
      onSuccess: (saved) => {
        resetTo(saved);
        Message.success('Saved.');
        void refreshProblems(queryClient);
      },
      onError: (failure) => {
        if (getHttpStatus(failure) === 409)
          return void conflict('content', getApiProblem(failure)?.detail);
        if (showFieldErrors(form, failure, CONTENT_FIELDS)) {
          const fields = getApiProblem(failure)?.errors?.map((e) => e.field) ?? [];
          setTab(firstErrorTab(fields) ?? tab);
          return;
        }
        Message.error(getApiProblem(failure)?.detail ?? 'The problem could not be saved.');
      },
    },
  });

  const saveTests = useSaveAdminProblemTests({
    mutation: {
      onSuccess: (result) => {
        queryClient.setQueryData(getListAdminProblemTestsQueryKey(problemId), result.testSet);
        if (result.changed) {
          Message.success(`Saved ${result.testSet.tests.length} tests as a new version.`);
          void refreshProblems(queryClient);
        } else {
          Message.info('Nothing to save: the tests are the same as before.');
          draft.discard();
        }
      },
      onError: (failure) => {
        if (getHttpStatus(failure) === 409)
          return void conflict('tests', getApiProblem(failure)?.detail);
        const problem = getApiProblem(failure);
        Message.error(
          problem?.errors?.[0]?.message ?? problem?.detail ?? 'The tests could not be saved.',
        );
      },
    },
  });

  const save = async () => {
    let values: ProblemContent;
    try {
      values = await form.validate();
    } catch (invalid) {
      setTab(firstErrorTab(Object.keys(invalid ?? {})) ?? tab);
      return;
    }
    const content = toContent(values);
    const send = () =>
      update.mutate({ id: problemId, data: { revision: baseline.revision, content } });
    const warnings: string[] = [];
    if (content.slug !== baseline.content.slug) {
      warnings.push(`Links to /problems/${baseline.content.slug} will stop working.`);
    }
    if (baseline.content.visible && !content.visible) {
      warnings.push('Users will no longer be able to open this problem or submit to it.');
    }
    if (warnings.length === 0) return send();
    Modal.confirm({
      title: 'Save these changes?',
      content: warnings.join(' '),
      okText: 'Save',
      onOk: send,
    });
  };

  const submitTests = () => {
    const { manifest, files } = buildManifest(draft.tests, testSet.version);
    saveTests.mutate({ id: problemId, data: { manifest, files } });
  };

  const unsaved: Tab[] = [];
  if (contentDirty) unsaved.push('details', 'statement', 'samples');
  if (draft.dirty) unsaved.push('tests');
  const content = baseline.content;

  return (
    <section>
      <AdminTabs />
      <div className={styles.header}>
        <Typography.Title heading={5}>
          {content.displayId}. {content.title}
        </Typography.Title>
        <Link to={`/problems/${content.slug}`}>View problem</Link>
      </div>
      {!content.visible && (
        <Alert
          type="warning"
          className={styles.notice}
          content="This problem is hidden: only admins can see it. Turn on Visible in Details and save to publish it."
        />
      )}
      <EditorTabs tab={tab} onChange={setTab} unsaved={unsaved} />
      <Form
        key={formKey}
        form={form}
        layout="vertical"
        initialValues={content}
        onValuesChange={(_, values) =>
          setContentDirty(!sameContent(values as ProblemContent, content))
        }
        hidden={tab === 'tests'}
      >
        <ContentFields tab={tab} showVisible />
        <div className={styles.actions}>
          <Button
            type="primary"
            loading={update.isPending}
            disabled={!contentDirty}
            onClick={() => void save()}
          >
            Save
          </Button>
          <Button disabled={!contentDirty} onClick={() => resetTo(baseline)}>
            Discard changes
          </Button>
        </div>
      </Form>
      <div hidden={tab !== 'tests'}>
        <TestsEditor problemId={problemId} draft={draft} />
        <div className={styles.actions}>
          <Button
            type="primary"
            loading={saveTests.isPending}
            disabled={!draft.dirty || !draft.complete}
            onClick={submitTests}
          >
            Save tests
          </Button>
          <Button disabled={!draft.dirty} onClick={draft.discard}>
            Discard test changes
          </Button>
        </div>
      </div>
    </section>
  );
}
