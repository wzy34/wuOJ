import { Button, Dropdown, Layout, Menu, Space, Tooltip } from '@arco-design/web-react';
import {
  IconDown,
  IconMoon,
  IconPoweroff,
  IconSettings,
  IconSun,
  IconUser,
} from '@arco-design/web-react/icon';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { loginPath, registerPath } from '@/features/auth/safeRedirect';
import { useCurrentUser, useRegistrationOpen } from '@/features/auth/useCurrentUser';
import { useSignOut } from '@/features/auth/useSignOut';
import { APP_NAME } from '@/lib/constants';
import { useEffectiveTheme } from '@/lib/theme';
import { usePreferences } from '@/stores/preferences';
import styles from './AppLayout.module.css';

const SECTIONS = [
  { key: '/problems', label: 'Problems' },
  { key: '/submissions', label: 'Submissions' },
];

const ADMIN_SECTION = { key: '/admin', label: 'Admin' };

function ThemeToggle() {
  const theme = useEffectiveTheme();
  const setTheme = usePreferences((state) => state.setTheme);
  const next = theme === 'dark' ? 'light' : 'dark';
  const label = `Switch to ${next} theme`;
  return (
    <Tooltip content={label}>
      <Button
        shape="circle"
        type="secondary"
        aria-label={label}
        icon={theme === 'dark' ? <IconSun /> : <IconMoon />}
        onClick={() => setTheme(next)}
      />
    </Tooltip>
  );
}

function UserMenu({ username }: { username: string }) {
  const navigate = useNavigate();
  const { signOut, isPending } = useSignOut();
  const menu = (
    <Menu
      onClickMenuItem={(key) => {
        if (key === 'sign-out') signOut();
        else void navigate(key);
      }}
    >
      <Menu.Item key={`/users/${encodeURIComponent(username)}`}>
        <IconUser style={{ marginRight: 8 }} />
        Profile
      </Menu.Item>
      <Menu.Item key="/settings">
        <IconSettings style={{ marginRight: 8 }} />
        Settings
      </Menu.Item>
      <Menu.Item key="sign-out" disabled={isPending}>
        <IconPoweroff style={{ marginRight: 8 }} />
        Sign out
      </Menu.Item>
    </Menu>
  );
  return (
    <Dropdown droplist={menu} position="br" trigger="click">
      <Button
        type="text"
        aria-label="User menu"
        className={styles.userButton}
        // Arco colours text buttons with the primary colour; the account name reads better neutral.
        style={{ color: 'var(--color-text-1)' }}
      >
        <IconUser />
        <span>{username}</span>
        <IconDown />
      </Button>
    </Dropdown>
  );
}

/** Sign in and register for visitors; both come back to the current page. */
function VisitorActions() {
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const registrationOpen = useRegistrationOpen();
  const here = pathname + search;
  return (
    <Space size={8}>
      <Button type="primary" onClick={() => void navigate(loginPath(here))}>
        Sign in
      </Button>
      {registrationOpen && (
        <Button type="secondary" onClick={() => void navigate(registerPath(here))}>
          Register
        </Button>
      )}
    </Space>
  );
}

/** Top bar plus page area for every page except sign-in and registration. */
export function AppLayout() {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const { user, isAdmin } = useCurrentUser();
  const sections = isAdmin ? [...SECTIONS, ADMIN_SECTION] : SECTIONS;
  const current = sections.find(
    (section) => pathname === section.key || pathname.startsWith(`${section.key}/`),
  );

  return (
    <Layout className={styles.layout}>
      <Layout.Header className={styles.header}>
        <Link to="/problems" className={styles.brand}>
          {APP_NAME}
        </Link>
        <Menu
          mode="horizontal"
          className={styles.nav}
          selectedKeys={current ? [current.key] : []}
          onClickMenuItem={(key) => void navigate(key === ADMIN_SECTION.key ? '/admin/users' : key)}
        >
          {sections.map((section) => (
            <Menu.Item key={section.key}>{section.label}</Menu.Item>
          ))}
        </Menu>
        <div className={styles.actions}>
          <ThemeToggle />
          {user ? <UserMenu username={user.username} /> : <VisitorActions />}
        </div>
      </Layout.Header>
      <Layout.Content className={styles.content}>
        <Outlet />
      </Layout.Content>
    </Layout>
  );
}
