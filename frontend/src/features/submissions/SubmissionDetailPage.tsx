import { Card, Descriptions, Space, Table, Typography } from '@arco-design/web-react';
import type { ColumnProps } from '@arco-design/web-react/es/Table';
import { Link, useParams } from 'react-router';
import type { SubmissionDetail, TestResult } from '@/api/generated/model';
import { getHttpStatus } from '@/api/http';
import { LazyCodeEditor } from '@/components/LazyCodeEditor';
import { CopyButton } from '@/components/CopyButton';
import { NotFoundState, PageError, PageLoading } from '@/components/PageState';
import { VerdictTag } from '@/components/VerdictTag';
import { formatDateTime, formatMemory, formatTime } from '@/lib/format';
import { usePageTitle } from '@/lib/usePageTitle';
import { runningProgress } from './progress';
import styles from './SubmissionDetailPage.module.css';
import { useSubmissionLive } from './useSubmissionLive';

const testColumns: ColumnProps<TestResult>[] = [
  { title: 'Test', dataIndex: 'testNumber', width: 80 },
  {
    title: 'Result',
    dataIndex: 'verdict',
    render: (_: unknown, result) => <VerdictTag status="FINISHED" verdict={result.verdict} />,
  },
  { title: 'Time', dataIndex: 'timeMs', width: 120, render: (ms: number) => formatTime(ms) },
  { title: 'Memory', dataIndex: 'memoryKb', width: 120, render: (kb: number) => formatMemory(kb) },
];

function Summary({ submission }: { submission: SubmissionDetail }) {
  return (
    <Descriptions
      column={{ xs: 1, sm: 2, md: 2, lg: 4, xl: 4, xxl: 4 }}
      layout="vertical"
      border
      data={[
        { label: 'User', value: submission.user.username },
        {
          label: 'Problem',
          value: (
            <Link to={`/problems/${submission.problem.slug}`}>
              {submission.problem.displayId}. {submission.problem.title}
            </Link>
          ),
        },
        { label: 'Language', value: 'C++17' },
        { label: 'Submitted', value: formatDateTime(submission.createdAt) },
        {
          label: 'Result',
          value: (
            <VerdictTag
              status={submission.status}
              verdict={submission.verdict}
              progress={runningProgress(submission)}
            />
          ),
        },
        { label: 'Time', value: formatTime(submission.timeMs) },
        { label: 'Memory', value: formatMemory(submission.memoryKb) },
      ]}
    />
  );
}

function SourceCode({ submission }: { submission: SubmissionDetail }) {
  if (submission.sourceCode === undefined) {
    return (
      <Typography.Text type="secondary">Source code is only visible to its author.</Typography.Text>
    );
  }
  return (
    <div className={styles.source}>
      <LazyCodeEditor value={submission.sourceCode} readOnly height={360} />
    </div>
  );
}

function Results({ submission }: { submission: SubmissionDetail }) {
  if (submission.verdict === 'CE') {
    return submission.compileOutput ? (
      <pre className={styles.compileOutput} aria-label="Compiler output">
        {submission.compileOutput}
      </pre>
    ) : (
      <Typography.Text type="secondary">The code did not compile.</Typography.Text>
    );
  }
  if (submission.testResults.length === 0) {
    return (
      <Typography.Text type="secondary">
        {submission.status === 'FINISHED' ? 'No tests were run.' : 'Waiting for the judge…'}
      </Typography.Text>
    );
  }
  return (
    <Table
      rowKey="testNumber"
      aria-label="Test results"
      columns={testColumns}
      data={submission.testResults}
      pagination={false}
      size="small"
    />
  );
}

export function SubmissionDetailPage() {
  const params = useParams();
  const id = Number(params.id);
  const validId = Number.isInteger(id) && id > 0;
  const submission = useSubmissionLive(validId ? id : undefined);
  usePageTitle(`Submission #${params.id}`);

  if (!validId || getHttpStatus(submission.error) === 404) {
    return (
      <NotFoundState
        title="Submission not found"
        linkTo="/submissions"
        linkText="Back to submissions"
      />
    );
  }
  if (submission.isPending) return <PageLoading />;
  if (submission.isError) return <PageError onRetry={() => void submission.refetch()} />;

  const data = submission.data;
  return (
    <section className={styles.page}>
      <Typography.Title heading={2} className={styles.title}>
        Submission #{data.id}
      </Typography.Title>
      <Summary submission={data} />
      <Card
        title="Source code"
        extra={
          data.sourceCode !== undefined && (
            <CopyButton text={data.sourceCode} label="Copy source code" />
          )
        }
      >
        <SourceCode submission={data} />
      </Card>
      <Card title={data.verdict === 'CE' ? 'Compiler output' : 'Test results'}>
        <Space direction="vertical" style={{ width: '100%' }}>
          <Results submission={data} />
        </Space>
      </Card>
    </section>
  );
}
