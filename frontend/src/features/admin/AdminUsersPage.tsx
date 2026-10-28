import {
  Button,
  Input,
  Message,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from '@arco-design/web-react';
import type { ColumnProps } from '@arco-design/web-react/es/Table';
import { keepPreviousData, useQueryClient } from '@tanstack/react-query';
import { useCallback, useState } from 'react';
import { Link } from 'react-router';
import {
  getListAdminUsersQueryKey,
  useListAdminUsers,
  useResetUserPassword,
  useUpdateAdminUser,
} from '@/api/generated/admin/admin';
import type { AdminUser, UserRole } from '@/api/generated/model';
import { getApiProblem } from '@/api/http';
import { PageEmpty, PageError, PageLoading } from '@/components/PageState';
import { useCurrentUser } from '@/features/auth/useCurrentUser';
import { formatDate, formatDateTime, formatFromNow } from '@/lib/format';
import { useListSearchParams } from '@/lib/searchParams';
import { usePageTitle } from '@/lib/usePageTitle';
import { useSearchBox } from '@/lib/useSearchBox';
import styles from './Admin.module.css';
import { AdminTabs } from './AdminTabs';
import { ResetPasswordDialog } from './ResetPasswordDialog';

const PAGE_SIZE = 20;
const SIGNED_OUT = 'They will be signed out everywhere.';
export const OWN_ACCOUNT = 'You cannot change your own account here.';

function StatusTag({ account }: { account: AdminUser }) {
  if (!account.enabled) return <Tag color="red">Disabled</Tag>;
  if (account.mustChangePassword) return <Tag color="orange">Must change password</Tag>;
  return <Tag color="green">Active</Tag>;
}

type Actions = {
  setRole: (account: AdminUser, role: UserRole) => void;
  setEnabled: (account: AdminUser, enabled: boolean) => void;
  resetPassword: (account: AdminUser) => void;
};

function Confirmed({
  title,
  onOk,
  disabled,
  status,
  children,
}: {
  title: string;
  onOk: () => void;
  disabled: boolean;
  status?: 'danger';
  children: string;
}) {
  return (
    <Popconfirm
      title={title}
      content={SIGNED_OUT}
      okText={children}
      onOk={onOk}
      disabled={disabled}
    >
      <Button size="small" status={status} disabled={disabled}>
        {children}
      </Button>
    </Popconfirm>
  );
}

function RowActions({
  account,
  self,
  actions,
}: {
  account: AdminUser;
  self: boolean;
  actions: Actions;
}) {
  const otherRole: UserRole = account.role === 'ADMIN' ? 'USER' : 'ADMIN';
  const buttons = (
    <Space size={8} wrap>
      <Confirmed
        title={`Make ${account.username} ${otherRole === 'ADMIN' ? 'an admin' : 'a user'}?`}
        onOk={() => actions.setRole(account, otherRole)}
        disabled={self}
      >
        {otherRole === 'ADMIN' ? 'Make admin' : 'Make user'}
      </Confirmed>
      <Confirmed
        title={`${account.enabled ? 'Disable' : 'Enable'} ${account.username}?`}
        onOk={() => actions.setEnabled(account, !account.enabled)}
        disabled={self}
        status={account.enabled ? 'danger' : undefined}
      >
        {account.enabled ? 'Disable' : 'Enable'}
      </Confirmed>
      <Confirmed
        title={`Reset the password of ${account.username}?`}
        onOk={() => actions.resetPassword(account)}
        disabled={self}
      >
        Reset password
      </Confirmed>
    </Space>
  );
  if (!self) return buttons;
  return (
    <Tooltip content={OWN_ACCOUNT}>
      <span>{buttons}</span>
    </Tooltip>
  );
}

function columns(selfId: number | undefined, actions: Actions): ColumnProps<AdminUser>[] {
  return [
    {
      title: 'Username',
      dataIndex: 'username',
      render: (username: string) => (
        <Link to={`/users/${encodeURIComponent(username)}`}>{username}</Link>
      ),
    },
    {
      title: 'Role',
      dataIndex: 'role',
      width: 100,
      render: (role: UserRole) => (role === 'ADMIN' ? 'Admin' : 'User'),
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      width: 190,
      render: (_: unknown, account) => <StatusTag account={account} />,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      width: 120,
      render: (createdAt: string) => (
        <span title={formatDateTime(createdAt)}>{formatDate(createdAt)}</span>
      ),
    },
    {
      title: 'Last sign-in',
      dataIndex: 'lastLoginAt',
      width: 150,
      render: (lastLoginAt?: string) =>
        lastLoginAt ? (
          <span title={formatDateTime(lastLoginAt)}>{formatFromNow(lastLoginAt)}</span>
        ) : (
          'Never'
        ),
    },
    {
      title: 'Actions',
      dataIndex: 'id',
      width: 360,
      render: (_: unknown, account) => (
        <RowActions account={account} self={account.id === selfId} actions={actions} />
      ),
    },
  ];
}

function readRole(value: string | null): UserRole | undefined {
  return value === 'USER' || value === 'ADMIN' ? value : undefined;
}

function readEnabled(value: string | null): boolean | undefined {
  return value === 'true' ? true : value === 'false' ? false : undefined;
}

export function AdminUsersPage() {
  usePageTitle('Accounts');
  const queryClient = useQueryClient();
  const { user } = useCurrentUser();
  const { params, page, update } = useListSearchParams();
  const q = params.get('q') ?? '';
  const role = readRole(params.get('role'));
  const enabled = readEnabled(params.get('enabled'));
  const applySearch = useCallback(
    (value: string) => update({ q: value }, { replace: true }),
    [update],
  );
  const [search, setSearch] = useSearchBox(q, applySearch);
  const [reset, setReset] = useState<{ username: string; password?: string }>();

  const accounts = useListAdminUsers(
    { page, size: PAGE_SIZE, q: q || undefined, role, enabled },
    { query: { placeholderData: keepPreviousData } },
  );

  const refresh = () => queryClient.invalidateQueries({ queryKey: getListAdminUsersQueryKey() });
  const refused = (failure: unknown) =>
    Message.error(getApiProblem(failure)?.detail ?? 'The change could not be made.');
  const updateUser = useUpdateAdminUser({
    mutation: {
      onSuccess: (account) => {
        Message.success(`Updated ${account.username}.`);
        void refresh();
      },
      onError: refused,
    },
  });
  const resetPassword = useResetUserPassword({
    mutation: {
      onSuccess: (result, { id }) => {
        const username = accounts.data?.items.find((a) => a.id === id)?.username ?? 'the user';
        setReset({ username, password: result.temporaryPassword });
        void refresh();
      },
      onError: refused,
    },
  });

  const actions: Actions = {
    setRole: (account, newRole) => updateUser.mutate({ id: account.id, data: { role: newRole } }),
    setEnabled: (account, on) => updateUser.mutate({ id: account.id, data: { enabled: on } }),
    resetPassword: (account) => resetPassword.mutate({ id: account.id }),
  };

  let body;
  if (accounts.isPending) {
    body = <PageLoading />;
  } else if (accounts.isError && !accounts.data) {
    body = <PageError onRetry={() => void accounts.refetch()} />;
  } else if (accounts.data.items.length === 0) {
    body = <PageEmpty description="No accounts match these filters" />;
  } else {
    body = (
      <Table
        rowKey="id"
        columns={columns(user?.id, actions)}
        data={accounts.data.items}
        loading={accounts.isFetching && accounts.isPlaceholderData}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: accounts.data.totalItems,
          showTotal: true,
          hideOnSinglePage: true,
        }}
        onChange={(pagination) => update({ page: pagination.current })}
        scroll={{ x: 1100 }}
      />
    );
  }

  return (
    <section>
      <AdminTabs />
      <div className={styles.filters}>
        <Input.Search
          allowClear
          aria-label="Search accounts by username"
          placeholder="Search by username"
          value={search}
          onChange={setSearch}
          maxLength={64}
          className={styles.search}
        />
        <Select
          aria-label="Filter by role"
          placeholder="All roles"
          allowClear
          value={role}
          onChange={(value?: string) => update({ role: value })}
          options={[
            { value: 'USER', label: 'Users' },
            { value: 'ADMIN', label: 'Admins' },
          ]}
          className={styles.filter}
        />
        <Select
          aria-label="Filter by status"
          placeholder="Any status"
          allowClear
          value={enabled === undefined ? undefined : String(enabled)}
          onChange={(value?: string) => update({ enabled: value })}
          options={[
            { value: 'true', label: 'Active' },
            { value: 'false', label: 'Disabled' },
          ]}
          className={styles.filter}
        />
      </div>
      {body}
      <ResetPasswordDialog
        username={reset?.username ?? ''}
        password={reset?.password}
        onClose={() => setReset(undefined)}
      />
    </section>
  );
}
