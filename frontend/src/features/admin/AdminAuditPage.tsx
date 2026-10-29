import { Table } from '@arco-design/web-react';
import type { ColumnProps } from '@arco-design/web-react/es/Table';
import { keepPreviousData } from '@tanstack/react-query';
import { Link } from 'react-router';
import { useListAuditEntries } from '@/api/generated/admin/admin';
import type { AuditEntry, UserRef } from '@/api/generated/model';
import { PageEmpty, PageError, PageLoading } from '@/components/PageState';
import { formatDateTime } from '@/lib/format';
import { useListSearchParams } from '@/lib/searchParams';
import { usePageTitle } from '@/lib/usePageTitle';
import { AdminTabs } from './AdminTabs';
import { describeAction } from './auditActions';

const PAGE_SIZE = 20;

function UserLink({ user }: { user: UserRef }) {
  return <Link to={`/users/${encodeURIComponent(user.username)}`}>{user.username}</Link>;
}

const columns: ColumnProps<AuditEntry>[] = [
  {
    title: 'Time',
    dataIndex: 'createdAt',
    width: 190,
    render: (createdAt: string) => formatDateTime(createdAt),
  },
  {
    title: 'Admin',
    dataIndex: 'actor',
    width: 160,
    render: (actor: UserRef) => <UserLink user={actor} />,
  },
  {
    title: 'Target',
    dataIndex: 'target',
    width: 220,
    // An account, or a problem (linking to its editor).
    render: (_: unknown, entry) =>
      entry.problem ? (
        <Link to={`/admin/problems/${entry.problem.id}`}>
          {entry.problem.displayId}. {entry.problem.title}
        </Link>
      ) : entry.target ? (
        <UserLink user={entry.target} />
      ) : null,
  },
  { title: 'Action', dataIndex: 'action', render: (_: unknown, entry) => describeAction(entry) },
];

export function AdminAuditPage() {
  usePageTitle('Audit log');
  const { page, update } = useListSearchParams();
  const entries = useListAuditEntries(
    { page, size: PAGE_SIZE },
    { query: { placeholderData: keepPreviousData } },
  );

  let body;
  if (entries.isPending) {
    body = <PageLoading />;
  } else if (entries.isError && !entries.data) {
    body = <PageError onRetry={() => void entries.refetch()} />;
  } else if (entries.data.items.length === 0) {
    body = <PageEmpty description="No admin actions yet" />;
  } else {
    body = (
      <Table
        rowKey="id"
        columns={columns}
        data={entries.data.items}
        loading={entries.isFetching && entries.isPlaceholderData}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: entries.data.totalItems,
          showTotal: true,
          hideOnSinglePage: true,
        }}
        onChange={(pagination) => update({ page: pagination.current })}
        scroll={{ x: 700 }}
      />
    );
  }

  return (
    <section>
      <AdminTabs />
      {body}
    </section>
  );
}
