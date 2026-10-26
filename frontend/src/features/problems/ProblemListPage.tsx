import {
  Button,
  Input,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from '@arco-design/web-react';
import type { ColumnProps } from '@arco-design/web-react/es/Table';
import { IconCheckCircleFill, IconMinusCircle } from '@arco-design/web-react/icon';
import { keepPreviousData } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useListProblems } from '@/api/generated/problems/problems';
import type { ProblemSummary, ProblemUserStatus } from '@/api/generated/model';
import { PageEmpty, PageError, PageLoading } from '@/components/PageState';
import { useListSearchParams } from '@/lib/searchParams';
import { usePageTitle } from '@/lib/usePageTitle';
import { DIFFICULTIES, DIFFICULTY_DISPLAY, isDifficulty } from './difficulty';
import styles from './ProblemListPage.module.css';

const PAGE_SIZE = 20;
const SEARCH_DELAY_MS = 300;

function StatusIcon({ status }: { status: ProblemUserStatus }) {
  if (status === 'NONE') return null;
  const solved = status === 'SOLVED';
  const label = solved ? 'Solved' : 'Attempted';
  return (
    <Tooltip content={label}>
      <span role="img" aria-label={label} className={solved ? styles.solved : styles.attempted}>
        {solved ? <IconCheckCircleFill /> : <IconMinusCircle />}
      </span>
    </Tooltip>
  );
}

const columns: ColumnProps<ProblemSummary>[] = [
  {
    title: 'Status',
    dataIndex: 'status',
    width: 80,
    align: 'center',
    render: (status: ProblemUserStatus) => <StatusIcon status={status} />,
  },
  { title: '#', dataIndex: 'displayId', width: 90 },
  {
    title: 'Title',
    dataIndex: 'title',
    render: (title: string, problem) => <Link to={`/problems/${problem.slug}`}>{title}</Link>,
  },
  {
    title: 'Difficulty',
    dataIndex: 'difficulty',
    width: 120,
    render: (difficulty: ProblemSummary['difficulty']) => (
      <Tag color={DIFFICULTY_DISPLAY[difficulty].color}>{DIFFICULTY_DISPLAY[difficulty].label}</Tag>
    ),
  },
];

export function ProblemListPage() {
  usePageTitle('Problems');
  const { params, page, update } = useListSearchParams();
  const q = params.get('q') ?? '';
  const difficultyParam = params.get('difficulty');
  const difficulty = isDifficulty(difficultyParam) ? difficultyParam : undefined;

  // The box updates immediately; the URL (and the request) follow once typing pauses.
  const [search, setSearch] = useState(q);
  // When the URL changes from outside (back button, "Clear search"), show its value in the box.
  const [shownQ, setShownQ] = useState(q);
  if (q !== shownQ) {
    setShownQ(q);
    setSearch(q);
  }
  useEffect(() => {
    if (search.trim() === q) return;
    const timer = setTimeout(
      () => update({ q: search.trim() }, { replace: true }),
      SEARCH_DELAY_MS,
    );
    return () => clearTimeout(timer);
  }, [search, q, update]);

  const problems = useListProblems(
    { page, size: PAGE_SIZE, q: q || undefined, difficulty },
    { query: { placeholderData: keepPreviousData } },
  );
  const filtered = Boolean(q || difficulty);

  let body;
  if (problems.isPending) {
    body = <PageLoading />;
  } else if (problems.isError && !problems.data) {
    body = <PageError onRetry={() => void problems.refetch()} />;
  } else if (problems.data.items.length === 0) {
    body = filtered ? (
      <PageEmpty
        description="No problems match your search"
        action={
          <Button onClick={() => update({ q: undefined, difficulty: undefined })}>
            Clear search
          </Button>
        }
      />
    ) : (
      <PageEmpty description="No problems yet" />
    );
  } else {
    body = (
      <Table
        rowKey="id"
        columns={columns}
        data={problems.data.items}
        loading={problems.isFetching}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: problems.data.totalItems,
          showTotal: true,
          hideOnSinglePage: true,
        }}
        onChange={(pagination) => update({ page: pagination.current })}
        scroll={{ x: 560 }}
      />
    );
  }

  return (
    <section>
      <div className={styles.header}>
        <Typography.Title heading={2} className={styles.title}>
          Problems
        </Typography.Title>
        <Space wrap>
          <Input.Search
            allowClear
            aria-label="Search problems by title"
            placeholder="Search by title"
            value={search}
            onChange={setSearch}
            maxLength={100}
            className={styles.search}
          />
          <Select
            aria-label="Filter by difficulty"
            placeholder="All difficulties"
            allowClear
            value={difficulty}
            onChange={(value?: string) => update({ difficulty: value })}
            options={DIFFICULTIES.map((d) => ({ value: d, label: DIFFICULTY_DISPLAY[d].label }))}
            className={styles.filter}
          />
        </Space>
      </div>
      {body}
    </section>
  );
}
