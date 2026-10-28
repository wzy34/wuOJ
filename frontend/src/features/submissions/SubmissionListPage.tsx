import { Radio, Select, Space, Table, Tag, Typography } from '@arco-design/web-react';
import type { ColumnProps } from '@arco-design/web-react/es/Table';
import { keepPreviousData } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import type { SubmissionSummary, Verdict } from '@/api/generated/model';
import { useGetProblem, useListProblems } from '@/api/generated/problems/problems';
import { useListSubmissions } from '@/api/generated/submissions/submissions';
import { PageEmpty, PageError, PageLoading } from '@/components/PageState';
import { useCurrentUser } from '@/features/auth/useCurrentUser';
import { VerdictTag } from '@/components/VerdictTag';
import { VERDICT_DISPLAY, VERDICT_OPTIONS } from '@/components/verdicts';
import { formatDateTime, formatFromNow, formatMemory, formatTime } from '@/lib/format';
import { useListSearchParams } from '@/lib/searchParams';
import { usePageTitle } from '@/lib/usePageTitle';
import styles from './SubmissionListPage.module.css';

const PAGE_SIZE = 20;
/** Refresh interval while any listed submission is still being judged. */
export const LIST_REFRESH_MS = 4000;

function isVerdict(value: string | null): value is Verdict {
  return value !== null && value in VERDICT_DISPLAY;
}

const columns: ColumnProps<SubmissionSummary>[] = [
  {
    title: 'ID',
    dataIndex: 'id',
    width: 90,
    render: (id: number) => <Link to={`/submissions/${id}`}>#{id}</Link>,
  },
  {
    title: 'Submitted',
    dataIndex: 'createdAt',
    width: 150,
    render: (createdAt: string) => (
      <span title={formatDateTime(createdAt)}>{formatFromNow(createdAt)}</span>
    ),
  },
  {
    title: 'User',
    dataIndex: 'user.username',
    width: 130,
    render: (_: unknown, s) => (
      <Link to={`/users/${encodeURIComponent(s.user.username)}`}>{s.user.username}</Link>
    ),
  },
  {
    title: 'Problem',
    dataIndex: 'problem.title',
    width: 260,
    ellipsis: true,
    render: (_: unknown, s) => {
      const label = `${s.problem.displayId}. ${s.problem.title}`;
      return (
        <Link to={`/problems/${s.problem.slug}`} title={label}>
          {label}
        </Link>
      );
    },
  },
  { title: 'Language', dataIndex: 'language', width: 100, render: () => 'C++17' },
  {
    title: 'Result',
    dataIndex: 'verdict',
    width: 210,
    render: (_: unknown, s) => <VerdictTag status={s.status} verdict={s.verdict} />,
  },
  { title: 'Time', dataIndex: 'timeMs', width: 90, render: (ms?: number) => formatTime(ms) },
  { title: 'Memory', dataIndex: 'memoryKb', width: 100, render: (kb?: number) => formatMemory(kb) },
];

/** Problem filter with title search; keeps the selected problem's label even when off-page. */
function ProblemFilter({ value, onChange }: { value?: string; onChange: (slug?: string) => void }) {
  const [search, setSearch] = useState('');
  const options = useListProblems({ q: search || undefined, size: 20 });
  const selected = useGetProblem(value ?? '', { query: { enabled: Boolean(value) } });
  const items = options.data?.items ?? [];
  const choices = [
    ...(selected.data && !items.some((p) => p.slug === selected.data.slug) ? [selected.data] : []),
    ...items,
  ].map((p) => ({ value: p.slug, label: `${p.displayId}. ${p.title}` }));

  return (
    <Select
      aria-label="Filter by problem"
      placeholder="All problems"
      allowClear
      showSearch
      filterOption={false}
      onSearch={setSearch}
      value={value}
      onChange={(slug?: string) => onChange(slug)}
      options={choices}
      loading={options.isFetching}
      className={styles.problemFilter}
    />
  );
}

export function SubmissionListPage() {
  usePageTitle('Submissions');
  const { user } = useCurrentUser();
  const { params, page, update } = useListSearchParams();
  // Visitors have no submissions of their own.
  const scope = params.get('scope') === 'all' || !user ? 'all' : 'mine';
  const problem = params.get('problem') ?? undefined;
  const byUser = params.get('user') ?? undefined;
  const verdictParam = params.get('verdict');
  const verdict = isVerdict(verdictParam) ? verdictParam : undefined;

  const submissions = useListSubmissions(
    { page, size: PAGE_SIZE, scope, problem, user: byUser, verdict },
    {
      query: {
        placeholderData: keepPreviousData,
        refetchInterval: (query) =>
          query.state.data?.items.some((s) => s.status !== 'FINISHED') ? LIST_REFRESH_MS : false,
      },
    },
  );

  let body;
  if (submissions.isPending) {
    body = <PageLoading />;
  } else if (submissions.isError && !submissions.data) {
    body = <PageError onRetry={() => void submissions.refetch()} />;
  } else if (submissions.data.items.length === 0) {
    body = <PageEmpty description="No submissions match these filters" />;
  } else {
    body = (
      <Table
        rowKey="id"
        columns={columns}
        data={submissions.data.items}
        loading={submissions.isFetching && submissions.isPlaceholderData}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: submissions.data.totalItems,
          showTotal: true,
          hideOnSinglePage: true,
        }}
        onChange={(pagination) => update({ page: pagination.current })}
        // Fixed column widths add up to about 1130 px; narrower windows scroll inside the table.
        scroll={{ x: 1130 }}
      />
    );
  }

  return (
    <section>
      <div className={styles.header}>
        <Typography.Title heading={2} className={styles.title}>
          Submissions
        </Typography.Title>
        <Space wrap>
          {byUser && (
            <Tag closable onClose={() => update({ user: undefined })} aria-label="User filter">
              User: {byUser}
            </Tag>
          )}
          {user && (
            <Radio.Group
              type="button"
              aria-label="Whose submissions"
              value={scope}
              onChange={(value: string) => update({ scope: value === 'all' ? 'all' : undefined })}
              options={[
                { value: 'mine', label: 'Mine' },
                { value: 'all', label: 'All users' },
              ]}
            />
          )}
          <ProblemFilter value={problem} onChange={(slug) => update({ problem: slug })} />
          <Select
            aria-label="Filter by verdict"
            placeholder="All verdicts"
            allowClear
            value={verdict}
            onChange={(value?: string) => update({ verdict: value })}
            options={VERDICT_OPTIONS}
            className={styles.verdictFilter}
          />
        </Space>
      </div>
      {body}
    </section>
  );
}
