import { Button, Input, Select, Table, Tag } from '@arco-design/web-react';
import type { ColumnProps } from '@arco-design/web-react/es/Table';
import { IconPlus } from '@arco-design/web-react/icon';
import { keepPreviousData } from '@tanstack/react-query';
import { useCallback } from 'react';
import { Link, useNavigate } from 'react-router';
import { useListAdminProblems } from '@/api/generated/admin/admin';
import type { AdminProblemSummary, Difficulty } from '@/api/generated/model';
import { PageEmpty, PageError, PageLoading } from '@/components/PageState';
import { DIFFICULTY_DISPLAY } from '@/features/problems/difficulty';
import { formatDateTime, formatFromNow } from '@/lib/format';
import { useListSearchParams } from '@/lib/searchParams';
import { usePageTitle } from '@/lib/usePageTitle';
import { useSearchBox } from '@/lib/useSearchBox';
import styles from '../Admin.module.css';
import { AdminTabs } from '../AdminTabs';

const PAGE_SIZE = 20;

const columns: ColumnProps<AdminProblemSummary>[] = [
  { title: 'ID', dataIndex: 'displayId', width: 90 },
  {
    title: 'Title',
    dataIndex: 'title',
    render: (title: string, problem) => <Link to={`/admin/problems/${problem.id}`}>{title}</Link>,
  },
  {
    title: 'Difficulty',
    dataIndex: 'difficulty',
    width: 110,
    render: (difficulty: Difficulty) => (
      <Tag color={DIFFICULTY_DISPLAY[difficulty].color}>{DIFFICULTY_DISPLAY[difficulty].label}</Tag>
    ),
  },
  {
    title: 'Visibility',
    dataIndex: 'visible',
    width: 110,
    render: (visible: boolean) =>
      visible ? <Tag color="green">Visible</Tag> : <Tag color="gray">Hidden</Tag>,
  },
  { title: 'Tests', dataIndex: 'testCount', width: 80 },
  {
    title: 'Last change',
    dataIndex: 'updatedAt',
    width: 150,
    render: (updatedAt: string) => (
      <span title={formatDateTime(updatedAt)}>{formatFromNow(updatedAt)}</span>
    ),
  },
];

function readVisible(value: string | null): boolean | undefined {
  return value === 'true' ? true : value === 'false' ? false : undefined;
}

export function AdminProblemsPage() {
  usePageTitle('Problems');
  const navigate = useNavigate();
  const { params, page, update } = useListSearchParams();
  const q = params.get('q') ?? '';
  const visible = readVisible(params.get('visible'));
  const applySearch = useCallback(
    (value: string) => update({ q: value }, { replace: true }),
    [update],
  );
  const [search, setSearch] = useSearchBox(q, applySearch);

  const problems = useListAdminProblems(
    { page, size: PAGE_SIZE, q: q || undefined, visible },
    { query: { placeholderData: keepPreviousData } },
  );

  let body;
  if (problems.isPending) {
    body = <PageLoading />;
  } else if (problems.isError && !problems.data) {
    body = <PageError onRetry={() => void problems.refetch()} />;
  } else if (problems.data.items.length === 0) {
    body = <PageEmpty description="No problems match these filters" />;
  } else {
    body = (
      <Table
        rowKey="id"
        columns={columns}
        data={problems.data.items}
        loading={problems.isFetching && problems.isPlaceholderData}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: problems.data.totalItems,
          showTotal: true,
          hideOnSinglePage: true,
        }}
        onChange={(pagination) => update({ page: pagination.current })}
        scroll={{ x: 800 }}
      />
    );
  }

  return (
    <section>
      <AdminTabs />
      <div className={styles.filters}>
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
          aria-label="Filter by visibility"
          placeholder="Visible and hidden"
          allowClear
          value={visible === undefined ? undefined : String(visible)}
          onChange={(value?: string) => update({ visible: value })}
          options={[
            { value: 'true', label: 'Visible' },
            { value: 'false', label: 'Hidden' },
          ]}
          className={styles.filter}
        />
        <Button
          type="primary"
          icon={<IconPlus />}
          className={styles.push}
          onClick={() => void navigate('/admin/problems/new')}
        >
          New problem
        </Button>
      </div>
      {body}
    </section>
  );
}
