import {
  Alert,
  Button,
  Popconfirm,
  ResizeBox,
  Select,
  Space,
  Tabs,
  Tag,
  Tooltip,
  Typography,
} from '@arco-design/web-react';
import { IconCheckCircleFill } from '@arco-design/web-react/icon';
import { useQueryClient } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import type { Problem } from '@/api/generated/model';
import { useGetProblem } from '@/api/generated/problems/problems';
import {
  getGetSubmissionQueryKey,
  useCreateSubmission,
} from '@/api/generated/submissions/submissions';
import { getApiProblem, getHttpStatus, getRetryAfterSeconds } from '@/api/http';
import { LazyCodeEditor } from '@/components/LazyCodeEditor';
import { MarkdownView } from '@/components/MarkdownView';
import { NotFoundState, PageError, PageLoading } from '@/components/PageState';
import { loginPath } from '@/features/auth/safeRedirect';
import { useCurrentUser } from '@/features/auth/useCurrentUser';
import { formatTimeLimit } from '@/lib/format';
import { useMediaQuery } from '@/lib/useMediaQuery';
import { usePageTitle } from '@/lib/usePageTitle';
import { EDITOR_FONT_SIZES, usePreferences } from '@/stores/preferences';
import { DIFFICULTY_DISPLAY } from './difficulty';
import { MySubmissions } from './MySubmissions';
import { ResultPanel } from './ResultPanel';
import { Samples } from './Samples';
import { draftKey, useDraft, type DraftOwner } from './useDraft';
import styles from './WorkspacePage.module.css';

export const MAX_SOURCE_LENGTH = 65_536;

function Statement({ problem }: { problem: Problem }) {
  const difficulty = DIFFICULTY_DISPLAY[problem.difficulty];
  return (
    <article>
      <Typography.Title heading={4} className={styles.title}>
        {problem.displayId}. {problem.title}
      </Typography.Title>
      <Space wrap className={styles.meta}>
        <Tag color={difficulty.color}>{difficulty.label}</Tag>
        <Tag>Time limit: {formatTimeLimit(problem.timeLimitMs)}</Tag>
        <Tag>Memory limit: {problem.memoryLimitMb} MB</Tag>
        {problem.status === 'SOLVED' && (
          <Tag color="green" icon={<IconCheckCircleFill />}>
            Solved
          </Tag>
        )}
      </Space>
      <MarkdownView>{problem.statement}</MarkdownView>
      {problem.samples.length > 0 && <Samples samples={problem.samples} />}
    </article>
  );
}

/** Visitors write code too; submitting needs an account, so the button leads to sign-in first. */
function SignInToSubmit() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  return (
    <Button type="primary" onClick={() => void navigate(loginPath(pathname))}>
      Sign in to submit
    </Button>
  );
}

function EditorPane({ problem, owner }: { problem: Problem; owner: DraftOwner }) {
  const queryClient = useQueryClient();
  const signedIn = owner !== 'guest';
  const { code, setCode, reset } = useDraft(owner, problem.id);
  const fontSize = usePreferences((state) => state.editorFontSize);
  const setFontSize = usePreferences((state) => state.setEditorFontSize);
  const [submissionId, setSubmissionId] = useState<number>();
  const [error, setError] = useState<string>();
  // Guards against a double click sending two submissions before the button re-renders.
  const sending = useRef(false);

  const create = useCreateSubmission({
    mutation: {
      onSuccess: (submission) => {
        queryClient.setQueryData(getGetSubmissionQueryKey(submission.id), submission);
        void queryClient.invalidateQueries({ queryKey: ['/api/submissions'] });
        setSubmissionId(submission.id);
      },
      onError: (failure) => {
        const status = getHttpStatus(failure);
        if (status === 429) {
          const seconds = getRetryAfterSeconds(failure);
          setError(
            seconds === undefined
              ? 'Too many submissions. Try again shortly.'
              : `Too many submissions. Try again in ${seconds} seconds.`,
          );
        } else if (status !== undefined && status < 500 && status !== 401) {
          setError(getApiProblem(failure)?.detail ?? 'The submission was rejected.');
        }
      },
      onSettled: () => {
        sending.current = false;
      },
    },
  });

  const blank = code.trim() === '';
  const submit = (source: string = code) => {
    if (source.trim() === '' || sending.current) return;
    if (source.length > MAX_SOURCE_LENGTH) {
      setError('Your code is larger than the 64 KB limit.');
      return;
    }
    sending.current = true;
    setError(undefined);
    create.mutate({ data: { problemId: problem.id, language: 'CPP17', sourceCode: source } });
  };

  return (
    <div className={styles.editorPane}>
      <div className={styles.toolbar}>
        <Space wrap>
          <Select
            aria-label="Language"
            value="CPP17"
            options={[{ value: 'CPP17', label: 'C++17' }]}
            className={styles.language}
            disabled
          />
          <Select
            aria-label="Editor font size"
            value={fontSize}
            onChange={setFontSize}
            options={EDITOR_FONT_SIZES.map((size) => ({ value: size, label: `${size} px` }))}
            className={styles.fontSize}
          />
          <Popconfirm
            title="Reset to template?"
            content="Your current code for this problem will be discarded."
            okText="Reset"
            onOk={reset}
          >
            <Button>Reset to template</Button>
          </Popconfirm>
        </Space>
        {signedIn ? (
          <Tooltip content="Ctrl+Enter (⌘+Enter on macOS)">
            <Button
              type="primary"
              onClick={() => submit()}
              disabled={blank}
              loading={create.isPending}
            >
              Submit
            </Button>
          </Tooltip>
        ) : (
          <SignInToSubmit />
        )}
      </div>
      {error && <Alert type="error" content={error} closable onClose={() => setError(undefined)} />}
      <div className={styles.editor}>
        <LazyCodeEditor
          value={code}
          onChange={setCode}
          onSubmitShortcut={signedIn ? submit : undefined}
        />
      </div>
      {signedIn && <ResultPanel submissionId={submissionId} />}
    </div>
  );
}

function StatementPane({ problem, signedIn }: { problem: Problem; signedIn: boolean }) {
  return (
    <Tabs defaultActiveTab="description" className={styles.tabs} lazyload>
      <Tabs.TabPane key="description" title="Description">
        <div className={styles.scroll}>
          <Statement problem={problem} />
        </div>
      </Tabs.TabPane>
      {signedIn && (
        <Tabs.TabPane key="submissions" title="My submissions">
          <div className={styles.scroll}>
            <MySubmissions slug={problem.slug} />
          </div>
        </Tabs.TabPane>
      )}
    </Tabs>
  );
}

export function WorkspacePage() {
  const { slug = '' } = useParams();
  const { user } = useCurrentUser();
  const problem = useGetProblem(slug);
  const narrow = useMediaQuery('(max-width: 767px)');
  usePageTitle(problem.data?.title ?? (problem.isError ? 'Problem not found' : undefined));

  if (problem.isPending) return <PageLoading />;
  if (problem.isError) {
    return getHttpStatus(problem.error) === 404 ? (
      <NotFoundState title="Problem not found" />
    ) : (
      <PageError onRetry={() => void problem.refetch()} />
    );
  }

  const owner: DraftOwner = user?.id ?? 'guest';
  const statement = <StatementPane problem={problem.data} signedIn={user !== undefined} />;
  const editor = (
    <EditorPane key={draftKey(owner, problem.data.id)} problem={problem.data} owner={owner} />
  );

  if (narrow) {
    return (
      <div className={styles.stacked}>
        <div className={styles.stackedStatement}>{statement}</div>
        <div className={styles.stackedEditor}>{editor}</div>
      </div>
    );
  }
  return (
    <ResizeBox.Split
      className={styles.split}
      direction="horizontal"
      size={0.45}
      min={0.25}
      max={0.75}
      panes={[
        <div key="statement" className={styles.pane}>
          {statement}
        </div>,
        <div key="editor" className={styles.pane}>
          {editor}
        </div>,
      ]}
    />
  );
}
